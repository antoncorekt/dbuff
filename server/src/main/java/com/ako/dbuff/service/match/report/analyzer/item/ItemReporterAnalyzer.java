package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.dao.model.ItemDomain;
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
public class ItemReporterAnalyzer implements ReportAnalyzer {

  private final List<ItemAnalyzer> itemAnalyzers;

  @Override
  public List<Report> analyze(MatchReportContext context) {
    List<Report> results = new ArrayList<>();

    for (Long playerId : context.getFocusPlayerIds()) {
      PlayerMatchStatisticDomain playerStat = context.getStatPerPlayer().get(playerId);
      if (playerStat == null) {
        continue;
      }
      List<ItemDomain> items = context.getItemsByPlayers().getOrDefault(playerId, List.of());

      for (ItemAnalyzer analyzer : itemAnalyzers) {
        results.addAll(analyzer.analyzePlayer(playerId, playerStat, items, context));
      }
    }

    return results;
  }

  @Override
  public String getAnalyzerName() {
    return "ItemReport";
  }

  @Override
  public int getOrder() {
    return 4;
  }
}
