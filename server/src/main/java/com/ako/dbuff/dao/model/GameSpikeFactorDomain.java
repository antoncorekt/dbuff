package com.ako.dbuff.dao.model;

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
 * One piece of evidence for a swing: a source of gold that moved, or a fight, a building or a
 * buyback inside it, or the item completed just before it. Shares overlap, so they are evidence,
 * not an exact split of the swing. Readers must ignore a factor type they do not know.
 */
@Entity
@Table(indexes = @Index(name = "idx_game_spike_factor_match", columnList = "matchId, spikeIndex"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSpikeFactorDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "game_spike_factor_seq")
  @SequenceGenerator(
      name = "game_spike_factor_seq",
      sequenceName = "game_spike_factor_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer spikeIndex;

  /** SOURCE, FIGHT, OBJECTIVE, BUYBACK or ITEM. */
  private String factorType;

  /** What it refers to: a gold source, a fight index, a building name or an item name. */
  private String ref;

  /** The player it concerns, when it concerns one. */
  private Long playerSlot;

  /** The gold it is worth to the benefiting team, null for a fight; the cost of an ITEM. */
  private Long goldValue;

  /** Its share of the swing; null when it has no gold value. */
  private Double sharePercent;

  /** Extra text; for an ITEM the second it was completed, as a number. */
  private String detail;

  private Integer analysisVersion;
}
