package com.ako.dbuff.service.match.report.analyzer.damage;

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
public class DamageReporterAnalyzer implements ReportAnalyzer {

  private final List<DamageAnalyzer> damageAnalyzers;

  @Override
  public List<Report> analyze(MatchReportContext context) {
    List<Report> results = new ArrayList<>();

    for (Long playerId : context.getFocusPlayerIds()) {
      PlayerMatchStatisticDomain playerStat = context.getStatPerPlayer().get(playerId);
      if (playerStat == null) {
        continue;
      }

      for (DamageAnalyzer analyzer : damageAnalyzers) {
        results.addAll(analyzer.analyzePlayer(playerId, playerStat, context));
      }
    }

    return results;
  }

  @Override
  public String getAnalyzerName() {
    return "DamageReport";
  }

  @Override
  public int getOrder() {
    return 3;
  }
}
