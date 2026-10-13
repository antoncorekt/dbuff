package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How well one whitelisted item, or item group, paid for one hero (see {@code
 * replay/item-efficiency.json}). The summary columns fit every kind of item; the totals particular
 * to a kind and the per-use rows are JSON text.
 */
@Entity
@Table(
    name = "item_efficiency",
    indexes = @Index(name = "idx_item_efficiency_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemEfficiencyDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_efficiency_seq")
  @SequenceGenerator(
      name = "item_efficiency_seq",
      sequenceName = "item_efficiency_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** The whitelist entry: an item key or a group name. */
  private String entryKey;

  /** The item the hero held; the first one of a group. */
  private String itemKey;

  /** The evaluator kind of the entry. */
  private String kind;

  /** Game seconds he first held the item; negative before the horn. */
  private Double purchaseSeconds;

  /** Uses; for a farming group, the creeps killed in the window after the purchase. */
  private Integer useCount;

  /** The gain in gold; experience counts at the entry's weight. */
  private Double gain;

  private Long cost;

  /** The value divided by the cost; null when it cannot be told. */
  private Double ratio;

  /** Game seconds the gain passed the cost; null when it never did. */
  private Double paybackSeconds;

  /** A JSON object of named totals, for example hp, mana, saves, gold, xp. */
  @Column(columnDefinition = "text")
  private String figures;

  /** A JSON object with the flags (what the data could not tell) and the per-use rows. */
  @Column(columnDefinition = "text")
  private String details;

  private Integer analysisVersion;
}
