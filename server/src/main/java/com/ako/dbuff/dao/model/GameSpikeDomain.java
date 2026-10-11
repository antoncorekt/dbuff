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

/** A stretch in which the net worth difference between the teams moved far and fast. */
@Entity
@Table(indexes = @Index(name = "idx_game_spike_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSpikeDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "game_spike_seq")
  @SequenceGenerator(name = "game_spike_seq", sequenceName = "game_spike_seq", allocationSize = 50)
  private Long id;

  private Long matchId;

  /** 0-based, in time order. */
  private Integer spikeIndex;

  private Integer startSeconds;
  private Integer endSeconds;

  /** RADIANT or DIRE: the team the swing favoured. */
  private String benefitingTeam;

  /** How far the net worth difference moved, always positive. */
  private Long deltaGold;

  /** The swing as a percentage of both teams' net worth together at its end. */
  private Double deltaPercent;

  /** Whether the lead changed hands. */
  private Boolean directionChange;

  /** The benefiting team's lead, negative when it was behind, before and after. */
  private Long leadBefore;

  private Long leadAfter;

  /** One of the SpikeCause values. */
  private String primaryCause;

  /** How much of the swing the primary cause explains, from 0 to 1. */
  private Double confidence;

  private Integer analysisVersion;
}
