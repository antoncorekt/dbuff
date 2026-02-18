package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.ItemId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Item purchased by a player in a match.
 *
 * <p>Primary key uses playerSlot instead of playerId because: - Anonymous players have null
 * accountId (stored as -1 in playerId) - Multiple anonymous players in the same match would have
 * the same playerId - playerSlot (0-9) is always unique within a match
 */
@Entity
@IdClass(ItemId.class)
@Builder
@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDomain {

  @Id private Long itemId;

  @Id private Long matchId;

  /** Player slot in the match (0-9). Part of the composite primary key. */
  @Id private Long playerSlot;

  /** Player account ID. Can be -1 for anonymous players. Not part of the primary key. */
  private Long playerId;

  private String itemName;
  private String itemPrettyName;
  private Long itemPurchaseTime;

  private boolean isNeutral;
  private String neutralEnhancement;

  /** Damage dealt by this item (from damage_inflictor). */
  private Long damageDealt;

  /** Damage received from this item (from damage_inflictor_received). */
  private Long damageReceived;

  /** Number of times this item was used/activated (from item_uses). */
  private Long useCount;
}
