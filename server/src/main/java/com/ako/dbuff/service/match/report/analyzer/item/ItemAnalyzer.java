package com.ako.dbuff.service.match.report.analyzer.item;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.List;

public interface ItemAnalyzer {

  List<ItemReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<ItemDomain> items,
      MatchReportContext context);
}
