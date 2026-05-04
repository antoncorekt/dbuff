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
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private Map<Long, String> playerNames;

  private Map<Long, PlayerMatchStatisticDomain> statPerPlayer;
  private Map<Long, List<AbilityDomain>> abilitiesByPlayers;
  private Map<Long, List<ItemDomain>> itemsByPlayers;
  private boolean isWon;

  public void computeDerivedFields() {
    this.statPerPlayer =
        playerStatistics.stream()
            .collect(
                Collectors.toMap(
                    PlayerMatchStatisticDomain::getPlayerId, Function.identity(), (a, b) -> a));

    this.abilitiesByPlayers =
        abilities.stream().collect(Collectors.groupingBy(AbilityDomain::getPlayerId));

    this.itemsByPlayers = items.stream().collect(Collectors.groupingBy(ItemDomain::getPlayerId));

    this.isWon = determineIsWon();
  }

  private boolean determineIsWon() {
    List<PlayerMatchStatisticDomain> focusStats =
        playerStatistics.stream().filter(s -> focusPlayerIds.contains(s.getPlayerId())).toList();

    if (focusStats.isEmpty()) {
      return false;
    }

    return focusStats.stream().allMatch(s -> s.getWin() != null && s.getWin() == 1);
  }
}
