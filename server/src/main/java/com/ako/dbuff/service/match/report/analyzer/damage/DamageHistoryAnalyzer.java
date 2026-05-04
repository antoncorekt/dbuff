package com.ako.dbuff.service.match.report.analyzer.damage;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class DamageHistoryAnalyzer implements DamageAnalyzer {

  private static final int HISTORY_MONTHS = 12;

  private final PlayerGameStatisticRepo playerGameStatisticRepo;

  @Override
  public List<DamageReport> analyzePlayer(
      long playerId, PlayerMatchStatisticDomain playerStat, MatchReportContext context) {

    Long heroId = playerStat.getHeroId();
    Long matchId = context.getMatch().getId();

    DamageHistoryReport report = new DamageHistoryReport();
    report.setPlayerId(playerId);
    report.setHeroPrettyName(playerStat.getHeroPrettyName());
    report.setHeroId(heroId);
    report.setCurrentHeroDamage(val(playerStat.getHeroDamage()));
    report.setCurrentTowerDamage(val(playerStat.getTowerDamage()));
    report.setCurrentHeroHealing(val(playerStat.getHeroHealing()));
    report.setCurrentDamageTaken(val(playerStat.getDamageTaken()));
    report.setHistoryMonths(HISTORY_MONTHS);

    long sinceEpoch = Instant.now().minus(HISTORY_MONTHS * 30L, ChronoUnit.DAYS).getEpochSecond();
    List<Object[]> rows =
        playerGameStatisticRepo.findAvgDamageStatsByPlayerAndHero(
            playerId, heroId, matchId, sinceEpoch);

    if (!rows.isEmpty() && rows.getFirst()[4] != null) {
      Object[] row = rows.getFirst();
      long matchCount = ((Number) row[4]).longValue();
      if (matchCount > 0) {
        report.setAvgHeroDamage(doubleVal(row[0]));
        report.setAvgTowerDamage(doubleVal(row[1]));
        report.setAvgHeroHealing(doubleVal(row[2]));
        report.setAvgDamageTaken(doubleVal(row[3]));
        report.setHistoricalMatchCount(matchCount);
      }
    }

    return List.of(report);
  }

  private static long val(Long v) {
    return v != null ? v : 0;
  }

  private static double doubleVal(Object v) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    return 0;
  }
}
