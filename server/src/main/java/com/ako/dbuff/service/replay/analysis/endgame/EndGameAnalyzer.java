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
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code end_game_summary} row per match: the winner, the largest net worth lead the
 * loser held and whether it threw it away, the fight the game turned on, the buybacks of the last
 * minutes and the final fight. Only the fights are required; without importance, conversion or
 * buyback rows the turning point, buybacks or final fight degrade instead of skipping the match.
 * Buyback readiness from gold is not computed: the replay gives net worth but not what is unspent,
 * so what a hero could have afforded is not known.
 */
@Slf4j
@Order(96)
@Component
@RequiredArgsConstructor
public class EndGameAnalyzer implements ReplayAnalyzer {
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
    return 2;
  }

  /** Only the fights are needed; the rest degrades when its tables are empty. */
  @Override
  public Set<String> requires() {
    return Set.of("fights");
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
    row.durationSeconds(duration).leadKnown(netWorth != null).thrown(false);
    if (netWorth == null) {
      log.info("Match {} has no complete net worth timeline, so no lead or throw", matchId);
    } else {
      lead(row, netWorth, winner, fights, importance, matchId);
    }
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

  private void lead(
      EndGameSummaryDomain.EndGameSummaryDomainBuilder row,
      TeamNetWorth netWorth,
      TeamSide winner,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance,
      long matchId) {
    double[] loser = netWorth.of(winner.opponent());
    double[] other = netWorth.of(winner);
    double[] gold = new double[loser.length];
    double[] share = new double[loser.length];
    for (int i = 0; i < gold.length; i++) {
      gold[i] = loser[i] - other[i];
      share[i] = gold[i] / (loser[i] + other[i]);
    }
    EndGameLead lead = EndGameLead.of(gold, share, properties.leadRules());
    row.heldSeconds(lead.heldSeconds()).lastLeadGold(Math.round(lead.lastGold()));
    if (!lead.everLed()) {
      return;
    }
    int peakSeconds = lead.peakMinute() * TeamNetWorth.STEP_SECONDS;
    row.peakLeadSeconds(peakSeconds)
        .peakLeadGold(Math.round(lead.peakGold()))
        .peakLeadPercent(Math.round(lead.peakShare() * 1000) / 10.0)
        .leadLost(lead.lost())
        .thrown(lead.lost() && lead.heldSeconds() >= properties.getThrowMinSeconds());
    if (lead.lost()) {
      turningPoint(row, winner, peakSeconds, fights, importance, matchId);
    }
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
    Map<TeamSide, Long> byTeam =
        buybacks.stream()
            .filter(b -> b.getSeconds() != null && b.getPlayerSlot() != null)
            .filter(b -> b.getSeconds() >= from)
            .collect(
                Collectors.groupingBy(
                    b -> TeamSide.ofSlot(b.getPlayerSlot()), Collectors.counting()));
    row.radiantBuybacks(byTeam.getOrDefault(TeamSide.RADIANT, 0L).intValue())
        .direBuybacks(byTeam.getOrDefault(TeamSide.DIRE, 0L).intValue())
        .buybackWindowSeconds(properties.getBuybackWindowSeconds());
  }

  private void finalFight(
      EndGameSummaryDomain.EndGameSummaryDomainBuilder row,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance) {
    Map<Integer, FightDomain> byIndex =
        fights.stream()
            .filter(f -> f.getStartSeconds() != null && f.getFightIndex() != null)
            .collect(
                Collectors.toMap(
                    FightDomain::getFightIndex,
                    Function.identity(),
                    BinaryOperator.minBy(
                        Comparator.comparing(
                            FightDomain::getId, Comparator.nullsLast(Comparator.naturalOrder())))));
    Comparator<FightDomain> byTime = Comparator.comparing(FightDomain::getStartSeconds);
    importance.stream()
        .filter(FightImportanceDomain::isImportant)
        .filter(i -> byIndex.containsKey(i.getFightIndex()))
        .max(Comparator.comparing(i -> byIndex.get(i.getFightIndex()).getStartSeconds()))
        .ifPresentOrElse(
            i ->
                row.finalFightIndex(i.getFightIndex())
                    .finalFightSeconds(byIndex.get(i.getFightIndex()).getStartSeconds())
                    .finalFightWinner(i.getWinningTeam()),
            () ->
                byIndex.values().stream()
                    .max(byTime)
                    .ifPresent(
                        f ->
                            row.finalFightIndex(f.getFightIndex())
                                .finalFightSeconds(f.getStartSeconds())
                                .finalFightWinner(f.getWinner())));
  }

  private static int duration(MatchDomain match, TeamNetWorth netWorth) {
    if (match.getDuration() != null) {
      return match.getDuration().intValue();
    }
    return netWorth == null ? 0 : (netWorth.radiant().length - 1) * TeamNetWorth.STEP_SECONDS;
  }
}
