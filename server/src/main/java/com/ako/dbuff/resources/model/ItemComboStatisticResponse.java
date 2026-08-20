package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics over the games in which a player held every one of a requested set of items. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemComboStatisticResponse {

  private Long playerId;
  private String playerName;

  /** Number of games containing every requested item. */
  private Long gamesFound;

  /** Match IDs of those games, for drill-down and for asserting exclusions in tests. */
  private Set<Long> matchIds;

  /** Win percentage across the combo games, 0–100. Zero when no games matched. */
  private BigDecimal winRate;

  /** Average KDA across the combo games, or null when no games matched. */
  private BigDecimal avgKda;

  /** Per-item detail, one entry per requested item that appeared. */
  private List<Member> members;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Member {
    private Long itemId;
    private String itemName;
    private String itemPrettyName;

    /** Average purchase time in seconds across the combo games. */
    private BigDecimal avgPurchaseTime;

    /** Average uses per game across the combo games, or null when no use data was recorded. */
    private BigDecimal avgUseCount;
  }
}
