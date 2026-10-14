package com.ako.dbuff.service.replay.analysis.endgame;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code end_game_summary} row per match: the winner, the largest net worth lead the
 * loser held and whether it threw it away, the fight the game turned on, the buybacks of the last
 * minutes and the final fight. Buyback readiness from gold is not computed: the replay gives net
 * worth but not what is unspent, so what a hero could have afforded is not known.
 */
@Slf4j
@Order(96)
@Component
@RequiredArgsConstructor
public class EndGameAnalyzer implements ReplayAnalyzer {
  private static final int MINUTE_SECONDS = 60;

  private final MatchRepo matchRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final ObjectiveConversionRepo conversionRepo;
  private final BuybackEventRepo buybackRepo;
  private final EndGameSummaryRepo summaryRepo;
  private final EndGameProperties properties;

  @Override
  public String name() {
    return "end-game";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights", "important-fights", "objectives", "buybacks");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (match == null || match.getRadiantWin() == null) {
      log.info("Match {} has no recorded winner, so no end game summary", matchId);
      return;
    }
    TeamSide winner = match.getRadiantWin() ? TeamSide.RADIANT : TeamSide.DIRE;
    List<FightDomain> fights = fightRepo.findByMatchId(matchId);
    List<FightImportanceDomain> importance = importanceRepo.findByMatchId(matchId);
    EndGameSummaryDomain.EndGameSummaryDomainBuilder row =
        EndGameSummaryDomain.builder()
            .matchId(matchId)
            .winnerTeam(winner.name())
            .analysisVersion(version());
    TeamNetWorth netWorth = TeamNetWorth.of(timelineRepo.findByMatchId(matchId));
    int duration = duration(match, netWorth);
    row.durationSeconds(duration);
    boolean thrown = false;
    if (netWorth == null) {
      log.info("Match {} has no complete net worth timeline, so no lead or throw", matchId);
    } else {
      EndGameLead lead = lead(netWorth, winner);
      thrown = lead.everLed() && lead.heldSeconds() >= properties.getThrowMinSeconds();
      row.heldSeconds(lead.heldSeconds());
      if (lead.everLed()) {
        row.peakLeadSeconds(lead.peakMinute() * MINUTE_SECONDS)
            .peakLeadGold(Math.round(lead.peakGold()))
            .peakLeadPercent(Math.round(lead.peakShare() * 1000) / 10.0)
            .leadLost(lead.lost());
        turningPoint(row, winner, lead.peakMinute() * MINUTE_SECONDS, fights, importance, matchId);
      }
    }
    row.thrown(thrown);
    if (duration > 0) {
      buybacks(row, buybackRepo.findByMatchId(matchId), duration);
    }
    finalFight(row, fights, importance);
    summaryRepo.save(row.build());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    summaryRepo.deleteByMatchId(matchId);
  }

  private EndGameLead lead(TeamNetWorth netWorth, TeamSide winner) {
    double[] loser = winner == TeamSide.RADIANT ? netWorth.dire() : netWorth.radiant();
    double[] other = winner == TeamSide.RADIANT ? netWorth.radiant() : netWorth.dire();
    double[] gold = new double[loser.length];
    double[] share = new double[loser.length];
    for (int i = 0; i < gold.length; i++) {
      gold[i] = loser[i] - other[i];
      share[i] = gold[i] / (loser[i] + other[i]);
    }
    return EndGameLead.of(gold, share, properties.getThrowLeadShare());
  }

  private void turningPoint(
      EndGameSummaryDomain.EndGameSummaryDomainBuilder row,
      TeamSide winner,
      double peakSeconds,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance,
      long matchId) {
    EndGameTurningPoint.pick(
            winner.name(), peakSeconds, fights, importance, conversionRepo.findByMatchId(matchId))
        .ifPresent(
            p -> {
              row.turningFightIndex(p.fight().getFightIndex())
                  .turningSeconds(p.fight().getStartSeconds())
                  .turningKind(p.kind())
                  .turningGoldChange(p.goldChange());
              if (p.conversion() != null) {
                row.objectiveKind(p.conversion().getObjectiveKind())
                    .objectiveKey(p.conversion().getObjectiveKey())
                    .objectiveSecondsAfter(p.conversion().getSecondsAfter());
              }
            });
  }

  private void buybacks(
      EndGameSummaryDomain.EndGameSummaryDomainBuilder row,
      List<BuybackEventDomain> buybacks,
      int duration) {
    double from = duration - properties.getBuybackWindowSeconds();
    List<BuybackEventDomain> inWindow =
        buybacks.stream()
            .filter(b -> b.getSeconds() != null && b.getPlayerSlot() != null)
            .filter(b -> b.getSeconds() >= from)
            .toList();
    row.radiantBuybacks(
            (int)
                inWindow.stream()
                    .filter(b -> TeamSide.ofSlot(b.getPlayerSlot()) == TeamSide.RADIANT)
                    .count())
        .direBuybacks(
            (int)
                inWindow.stream()
                    .filter(b -> TeamSide.ofSlot(b.getPlayerSlot()) == TeamSide.DIRE)
                    .count())
        .buybackWindowSeconds(properties.getBuybackWindowSeconds());
  }

  private void finalFight(
      EndGameSummaryDomain.EndGameSummaryDomainBuilder row,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance) {
    Comparator<FightDomain> byTime = Comparator.comparing(FightDomain::getStartSeconds);
    List<FightDomain> timed = fights.stream().filter(f -> f.getStartSeconds() != null).toList();
    Optional<FightImportanceDomain> lastImportant =
        importance.stream()
            .filter(FightImportanceDomain::isImportant)
            .filter(i -> fightAt(timed, i.getFightIndex()) != null)
            .max(Comparator.comparing(i -> fightAt(timed, i.getFightIndex()).getStartSeconds()));
    if (lastImportant.isPresent()) {
      FightDomain fight = fightAt(timed, lastImportant.get().getFightIndex());
      row.finalFightIndex(fight.getFightIndex())
          .finalFightSeconds(fight.getStartSeconds())
          .finalFightWinner(lastImportant.get().getWinningTeam());
      return;
    }
    timed.stream()
        .max(byTime)
        .ifPresent(
            f ->
                row.finalFightIndex(f.getFightIndex())
                    .finalFightSeconds(f.getStartSeconds())
                    .finalFightWinner(f.getWinner()));
  }

  private static FightDomain fightAt(List<FightDomain> fights, Integer index) {
    return fights.stream()
        .filter(f -> Objects.equals(f.getFightIndex(), index))
        .findFirst()
        .orElse(null);
  }

  private static int duration(MatchDomain match, TeamNetWorth netWorth) {
    if (match.getDuration() != null) {
      return match.getDuration().intValue();
    }
    return netWorth == null ? 0 : (netWorth.radiant().length - 1) * MINUTE_SECONDS;
  }
}
