package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** One item purchase, unlike ItemDomain which keeps a single row per item. */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "purchaseIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPurchaseEventDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_purchase_event_seq")
  @SequenceGenerator(
      name = "item_purchase_event_seq",
      sequenceName = "item_purchase_event_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Long playerId;

  /** Order of the purchase within the player log. */
  private Integer purchaseIndex;

  /** Game time in seconds; negative before the horn. */
  private Long purchaseTime;

  /** Internal item name without the item_ prefix. */
  private String itemKey;

  private Long charges;
}
