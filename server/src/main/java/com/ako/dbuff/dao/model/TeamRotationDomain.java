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
 * One hero's trip away from his team's group that ended in another lane and came to something: a
 * fight, a kill, a death, or a lane left to the enemy's creeps. Plain walking between lanes is not
 * stored.
 */
@Entity
@Table(
    name = "team_rotation",
    indexes = @Index(name = "idx_team_rotation_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRotationDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_rotation_seq")
  @SequenceGenerator(
      name = "team_rotation_seq",
      sequenceName = "team_rotation_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Long playerSlot;
  private Integer startSeconds;
  private Integer endSeconds;

  /** One of the AwayTrip.End values. */
  private String endReason;

  /** BASE, LANE, RIVER or JUNGLE where he stood when he left; the lane is set for a lane. */
  private String fromZone;

  private String fromLane;

  /** Where he got; always a lane. {@code toSide} is OWN or ENEMY, the half of the map. */
  private String toZone;

  private String toLane;
  private String toSide;

  /** The zone of the team's group when he left. */
  private String groupZone;

  private String groupLane;

  /** A teleport ended inside the trip. */
  private boolean teleported;

  /** The lane he was laning in when he left; null without one. */
  private String laneLeft;

  private Integer kills;
  private Integer creepKills;
  private Integer fights;
  private Integer fightsWon;

  /** The first won fight of the trip, else the first fight; null without one. */
  private Integer fightIndex;

  /**
   * Creeps his lane partners and the enemy heroes of the lane killed after he left; both null if no
   * lane.
   */
  private Integer proxyOwnKills;

  private Integer proxyEnemyKills;

  /** The RotationReason values that applied, comma separated; empty for none. */
  private String reasons;

  /**
   * Gold and experience per minute over the trip and a tail after it (during), and over the stretch
   * of the same length before it (before); null when the totals did not reach.
   */
  private Integer incomeBefore;

  private Integer incomeDuring;
  private Integer xpBefore;
  private Integer xpDuring;

  /** One of the RotationVerdict values. */
  private String verdict;

  private Integer analysisVersion;
}
