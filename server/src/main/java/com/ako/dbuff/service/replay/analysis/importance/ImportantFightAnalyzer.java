package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.importance.ImportantFightRule.Measurement;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code fight_importance} row per fight with a decided winner: how far the fight moved
 * the gold and experience advantage graphs in the winner's favour, and whether that makes it
 * important. The reasons stay stored for every fight; the readers pick the important ones.
 */
@Slf4j
@Order(75)
@Component
@RequiredArgsConstructor
public class ImportantFightAnalyzer implements ReplayAnalyzer {
  private final FightRepo fightRepo;
  private final AdvantageSeriesReader seriesReader;
  private final ImportantFightRule rule;
  private final FightImportanceRepo importanceRepo;

  @Override
  public String name() {
    return "important-fights";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights", "fight-loss");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Optional<AdvantageSeries> series = seriesReader.read(matchId);
    if (series.isEmpty()) {
      log.info("Match {} has no complete advantage series, so no fight importance", matchId);
      return;
    }
    List<FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getFightIndex() != null && f.getStartSeconds() != null)
            .sorted(Comparator.comparing(FightDomain::getFightIndex))
            .toList();
    for (FightDomain fight : fights) {
      TeamSide winner = Enums.find(TeamSide.class, fight.getWinner());
      if (winner == null) {
        continue;
      }
      double end = fight.getEndSeconds() == null ? fight.getStartSeconds() : fight.getEndSeconds();
      Measurement m = rule.measure(series.get(), winner, fight.getStartSeconds(), end);
      importanceRepo.save(
          FightImportanceDomain.builder()
              .matchId(matchId)
              .fightIndex(fight.getFightIndex())
              .winningTeam(winner.name())
              .goldChange(m.goldChange())
              .xpChange(m.xpChange())
              .netWorth(m.netWorth())
              .sharePercent(Math.round(m.sharePercent() * 10) / 10.0)
              .leadFlip(m.leadFlip())
              .flipToWinner(m.flipToWinner())
              .slopeBreak(m.slopeBreak())
              .important(rule.isImportant(m))
              .analysisVersion(version())
              .build());
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    importanceRepo.deleteByMatchId(matchId);
  }
}
