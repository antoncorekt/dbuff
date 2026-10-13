package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The lane one hero played and the window he played it in. A hero who has no lane has a row with
 * the lane and window empty.
 */
@Entity
@Table(
    name = "lane_assignment",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}),
    indexes = @Index(name = "idx_lane_assignment_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaneAssignmentDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lane_assignment_seq")
  @SequenceGenerator(
      name = "lane_assignment_seq",
      sequenceName = "lane_assignment_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** RADIANT or DIRE. */
  private String team;

  /** BOTTOM, MID or TOP; null for a hero with no lane (a roamer or a jungler). */
  private String lane;

  /** The lane pair of both teams in it (one per lane); null without a lane. */
  private Integer pairId;

  /** SAFE, MID or OFF, from the point of view of his own team. */
  private String position;

  /** Game seconds the hero's laning began and ended; null without a lane. */
  private Integer startSeconds;

  private Integer endSeconds;

  /** One of the LaneEnd values: why his laning ended. */
  private String endReason;

  private Integer analysisVersion;
}
