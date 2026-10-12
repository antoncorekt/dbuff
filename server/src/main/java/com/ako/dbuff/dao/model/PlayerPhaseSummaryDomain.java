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
 * What one player did in one phase of the game (laning, mid game, late game): seconds spent in each
 * activity, kills, deaths and assists, income, and how many of the fights of the phase he could
 * have joined and did.
 */
@Entity
@Table(indexes = @Index(name = "idx_player_phase_summary_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPhaseSummaryDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_phase_summary_seq")
  @SequenceGenerator(
      name = "player_phase_summary_seq",
      sequenceName = "player_phase_summary_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** LANE, MID or LATE. */
  private String phase;

  private Integer startSeconds;
  private Integer endSeconds;

  /** The seconds of the phase with no activity recorded, so the columns below add up to it. */
  private Integer unknownSeconds;

  private Integer deadSeconds;
  private Integer teleportingSeconds;
  private Integer inBaseSeconds;
  private Integer jungleFarmSeconds;
  private Integer laneFarmSeconds;
  private Integer pushingSeconds;
  private Integer travelingSeconds;
  private Integer positionedSeconds;

  /** Counted from the replay's per-minute counters; null for a counter the replay lacks. */
  private Integer kills;

  private Integer deaths;
  private Integer assists;

  /** Gold and experience per minute over the phase, from OpenDota's per-minute series. */
  private Double goldPerMinute;

  private Double xpPerMinute;

  /** Fights that began in the phase while he was alive, and those he took part in. */
  private Integer fightsAvailable;

  private Integer fightsJoined;
  private Integer analysisVersion;
}
