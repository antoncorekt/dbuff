package com.ako.dbuff.service.match.report.analyzer.damage;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.List;

public interface DamageAnalyzer {

  List<DamageReport> analyzePlayer(
      long playerId, PlayerMatchStatisticDomain playerStat, MatchReportContext context);
}
