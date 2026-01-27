package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for ability ranking per player. Contains statistics about ability usage across
 * matches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbilityRankingResponse {

  /** Ability ID from AbilityDomain */
  private Long abilityId;

  /** Ability internal name */
  private String abilityName;

  /** Ability display name */
  private String abilityPrettyName;

  /** Player account ID */
  private Long playerId;

  /** Player name */
  private String playerName;

  /** Number of matches where this ability was picked */
  private Long pickCount;

  /** Percentage of matches where this ability was picked (0-100) */
  private BigDecimal pickRate;

  /** Win rate when this ability was picked (0-100) */
  private BigDecimal winRate;
}
