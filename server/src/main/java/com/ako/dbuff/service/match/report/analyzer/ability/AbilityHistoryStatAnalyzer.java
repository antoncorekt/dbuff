package com.ako.dbuff.service.match.report.analyzer.ability;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class AbilityHistoryStatAnalyzer implements AbilityAnalyzer {

  private final AbilityRepo abilityRepo;

  @Override
  public List<AbilityReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<AbilityDomain> abilities,
      MatchReportContext context) {

    Long heroId = playerStat.getHeroId();
    Long matchId = context.getMatch().getId();

    List<Object[]> avgRows = abilityRepo.findAvgStatsByPlayerAbilities(playerId, matchId, matchId);

    if (avgRows.isEmpty()) {
      return List.of();
    }

    Map<Long, Object[]> avgByAbilityId =
        avgRows.stream()
            .collect(Collectors.toMap(row -> (Long) row[0], Function.identity(), (a, b) -> a));

    Map<Long, AbilityDomain> currentByAbilityId =
        abilities.stream()
            .collect(
                Collectors.toMap(AbilityDomain::getAbilityId, Function.identity(), (a, b) -> a));

    List<AbilityReport> reports = new ArrayList<>();

    for (var entry : currentByAbilityId.entrySet()) {
      Long abilityId = entry.getKey();
      AbilityDomain current = entry.getValue();
      Object[] avg = avgByAbilityId.get(abilityId);

      if (avg == null) {
        continue;
      }

      AbilityHistoryStatReport report = new AbilityHistoryStatReport();
      report.setPlayerId(playerId);
      report.setAbilityId(abilityId);
      report.setPrettyAbilityName(current.getPrettyName());
      report.setHeroPrettyName(playerStat.getHeroPrettyName());
      report.setHeroId(heroId);
      report.setCurrentDamageDealt(val(current.getDamageDealt()));
      report.setCurrentDamageReceived(val(current.getDamageReceived()));
      report.setCurrentUseCount(val(current.getUseCount()));
      report.setAvgDamageDealt(doubleVal(avg[3]));
      report.setAvgDamageReceived(doubleVal(avg[4]));
      report.setAvgUseCount(doubleVal(avg[5]));
      report.setHistoricalMatchCount((Long) avg[6]);

      reports.add(report);
    }

    return reports;
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
