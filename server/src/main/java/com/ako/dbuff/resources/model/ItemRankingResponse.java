package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for item ranking per player. Contains statistics about item usage across matches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRankingResponse {

  /** Item ID from ItemDomain */
  private Long itemId;

  /** Item internal name */
  private String itemName;

  /** Item display name */
  private String itemPrettyName;

  /** Player account ID */
  private Long playerId;

  /** Player name */
  private String playerName;

  /** Number of matches where this item was picked */
  private Long pickCount;

  /** Percentage of matches where this item was picked (0-100) */
  private BigDecimal pickRate;

  /** Win rate when this item was picked (0-100) */
  private BigDecimal winRate;

  /** Average purchase time in seconds */
  private BigDecimal avgPurchaseTime;
}
