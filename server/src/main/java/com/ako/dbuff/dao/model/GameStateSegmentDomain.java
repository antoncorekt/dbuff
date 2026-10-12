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

/** A stretch of the game in which a team was ahead, behind or about even on net worth. */
@Entity
@Table(indexes = @Index(name = "idx_game_state_segment_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateSegmentDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "game_state_segment_seq")
  @SequenceGenerator(
      name = "game_state_segment_seq",
      sequenceName = "game_state_segment_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE; both teams get the mirrored segments. */
  private String team;

  private Integer startSeconds;
  private Integer endSeconds;

  /** AHEAD, EVEN or BEHIND, for that team. */
  private String state;

  /** The lead furthest from zero in the stretch, as a percentage of both nets worth together. */
  private Double peakLeadPercent;

  private Integer analysisVersion;
}
