package com.ako.dbuff.service.match.report.analyzer.ability;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class ChosenAbilityAnalyzer implements AbilityAnalyzer {

  private static final int HISTORY_MONTHS = 12;

  private final AbilityRepo abilityRepo;

  @Override
  public List<AbilityReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<AbilityDomain> abilities,
      MatchReportContext context) {

    List<AbilityReport> reports = new ArrayList<>();
    Long heroId = playerStat.getHeroId();
    Long matchId = context.getMatch().getId();
    long sinceEpoch = Instant.now().minus(HISTORY_MONTHS * 30L, ChronoUnit.DAYS).getEpochSecond();

    for (AbilityDomain ability : abilities) {
      ChosenAbilityPlayerReport report = new ChosenAbilityPlayerReport();
      report.setPlayerId(playerId);
      report.setAbilityId(ability.getAbilityId());
      report.setPrettyAbilityName(ability.getPrettyName());
      report.setHeroPrettyName(playerStat.getHeroPrettyName());
      report.setHeroId(heroId);
      report.setHistoryMonths(HISTORY_MONTHS);

      List<Object[]> winLossRows =
          abilityRepo.countWinLossByPlayerAbility(
              playerId, ability.getAbilityId(), matchId, sinceEpoch);

      int winCount = 0;
      int loseCount = 0;

      for (Object[] row : winLossRows) {
        Long win = (Long) row[0];
        Long count = (Long) row[1];
        if (win != null && win == 1) {
          winCount = count.intValue();
        } else {
          loseCount = count.intValue();
        }
      }

      int totalMatches = winCount + loseCount;
      double winRate = totalMatches > 0 ? (double) winCount / totalMatches : 0;

      report.setTotalMatches(totalMatches);
      report.setWinCount(winCount);
      report.setLoseCount(loseCount);
      report.setWinRate(winRate);

      reports.add(report);
    }

    return reports;
  }
}
