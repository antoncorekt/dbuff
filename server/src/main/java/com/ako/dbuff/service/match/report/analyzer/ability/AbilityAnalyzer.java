package com.ako.dbuff.service.match.report.analyzer.ability;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.List;

public interface AbilityAnalyzer {

  List<AbilityReport> analyzePlayer(
      long playerId,
      PlayerMatchStatisticDomain playerStat,
      List<AbilityDomain> abilities,
      MatchReportContext context);
}
