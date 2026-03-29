package com.ako.dbuff.resources.model;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MatchDetailResponse {

  private MatchDomain match;
  private List<PlayerMatchStatisticDomain> players;
  private List<ItemDomain> items;
  private List<AbilityDomain> abilities;
  private List<KillLogDomain> killLogs;
  private MatchAnalysisDomain analysis;
}
