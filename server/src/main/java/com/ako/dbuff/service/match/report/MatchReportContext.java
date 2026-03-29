package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchReportContext {

  private MatchDomain match;
  private List<PlayerMatchStatisticDomain> playerStatistics;
  private List<AbilityDomain> abilities;
  private List<ItemDomain> items;
  private List<KillLogDomain> killLogs;
  private DbufInstanceConfigDomain instanceConfig;
  private Set<Long> focusPlayerIds;
  /** Map of playerId -> player name for display purposes. */
  private Map<Long, String> playerNames;
}
