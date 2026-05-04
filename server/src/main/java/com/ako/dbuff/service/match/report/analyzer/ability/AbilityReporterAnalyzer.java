package com.ako.dbuff.service.match.report.analyzer.ability;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class AbilityReporterAnalyzer implements ReportAnalyzer {

  private final List<AbilityAnalyzer> abilityAnalyzers;

  @Override
  public List<Report> analyze(MatchReportContext context) {
    List<Report> results = new ArrayList<>();

    for (Long playerId : context.getFocusPlayerIds()) {
      PlayerMatchStatisticDomain playerStat = context.getStatPerPlayer().get(playerId);
      if (playerStat == null) {
        continue;
      }
      List<AbilityDomain> abilities =
          context.getAbilitiesByPlayers().getOrDefault(playerId, List.of());

      for (AbilityAnalyzer analyzer : abilityAnalyzers) {
        results.addAll(analyzer.analyzePlayer(playerId, playerStat, abilities, context));
      }
    }

    return results;
  }

  @Override
  public String getAnalyzerName() {
    return "AbilityReport";
  }

  @Override
  public int getOrder() {
    return 2;
  }
}
