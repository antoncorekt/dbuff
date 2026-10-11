package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A stretch of game time in which one hero did one thing in one kind of place; it covers {@code
 * [startSeconds, endSeconds)}. Worked out from the stored tracks and combat log, so it can be
 * worked out again with other rules; {@code analysisVersion} says which rules made a row.
 */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "startSeconds"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerActivitySegmentDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_activity_segment_seq")
  @SequenceGenerator(
      name = "player_activity_segment_seq",
      sequenceName = "player_activity_segment_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Integer startSeconds;
  private Integer endSeconds;

  /** One of the Activity values. */
  private String activity;

  /** BASE, LANE, RIVER or JUNGLE. */
  private String zone;

  /** BOTTOM, MID or TOP when the zone is a lane. */
  private String lane;

  /** OWN or ENEMY half of the map; null in the river. */
  private String side;

  private Integer analysisVersion;
}
