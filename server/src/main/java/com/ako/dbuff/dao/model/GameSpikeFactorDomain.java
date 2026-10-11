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
 * buyback inside it. Shares overlap, so they are evidence, not an exact split of the swing.
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

  /** SOURCE, FIGHT, OBJECTIVE or BUYBACK. */
  private String factorType;

  /** What it refers to: a gold source, a fight index or a building name. */
  private String ref;

  /** The player it concerns, when it concerns one. */
  private Long playerSlot;

  /** The gold it is worth to the benefiting team, null for a fight. */
  private Long goldValue;

  /** Its share of the swing; null when it has no gold value. */
  private Double sharePercent;

  private String detail;
  private Integer analysisVersion;
}
