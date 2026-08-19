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
}
