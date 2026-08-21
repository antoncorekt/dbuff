package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics over the games in which a player used every one of a requested set of abilities. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AbilityComboStatisticResponse {

  private Long playerId;
  private String playerName;

  /** Number of games containing every requested ability. */
  private Long gamesFound;

  /** Match IDs of those games. */
  private Set<Long> matchIds;

  /** Win percentage across the combo games, 0–100. Zero when no games matched. */
  private BigDecimal winRate;

  /** Average KDA across the combo games, or null when no games matched. */
  private BigDecimal avgKda;

  private List<Member> members;

  /**
   * Per-item figures over the same games, when the request also named items. Empty when it did not.
   *
   * <p>Separate from {@link #members} rather than a shared "member" shape because an item has a
   * purchase time and an ability does not, and flattening the two would mean rendering {@code —}
   * for a column that can never apply.
   */
  private List<ItemMember> itemMembers;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Member {
    private Long abilityId;
    private String abilityName;
    private String abilityPrettyName;

    /** Average uses per game across the combo games, or null when no use data was recorded. */
    private BigDecimal avgUseCount;
  }

  /** One item of a skill-plus-item request, averaged over the games satisfying both. */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemMember {
    private Long itemId;
    private String itemName;
    private String itemPrettyName;

    /** Average purchase time in seconds, or null when none was recorded. */
    private BigDecimal avgPurchaseTime;

    /** Average uses per game, or null when no use data was recorded. */
    private BigDecimal avgUseCount;
  }
}
