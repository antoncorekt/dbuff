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

/** How one side of one lane did against the other side, with the numbers behind it. */
@Entity
@Table(
    name = "lane_result",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "pairId", "team"}),
    indexes = @Index(name = "idx_lane_result_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaneResultDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lane_result_seq")
  @SequenceGenerator(
      name = "lane_result_seq",
      sequenceName = "lane_result_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer pairId;

  /** BOTTOM, MID or TOP. */
  private String lane;

  /** RADIANT or DIRE: the side this row is about. */
  private String team;

  /** SAFE, MID or OFF, from this side's point of view. */
  private String position;

  private Integer heroes;
  private Integer enemyHeroes;

  /** The lane pair's window: from the first hero arriving to the last one leaving. */
  private Integer startSeconds;

  private Integer endSeconds;

  /** One of the LaneEnd values: what ended the last hero's laning. */
  private String endReason;

  /** When the last hero of this side left, and why. */
  private Integer sideEndSeconds;

  private String sideEndReason;
  private Double creepScore;
  private Integer lastHits;
  private Integer siegeLastHits;
  private Integer denies;
  private Double xp;
  private Double gold;

  /** The average level of the side's heroes when each one's window ended. */
  private Double level;

  private Integer kills;
  private Integer deaths;
  private Long heroDamageDealt;
  private Long heroDamageTaken;
  private Long towerDamage;

  /** Whether this side's own tower of the lane fell in the window, or the other side's. */
  private Boolean ownTowerFell;

  private Boolean enemyTowerFell;

  /** One of the LaneResult values. */
  private String result;

  /** The weighted lead of this side, from -1 to about 1; the other side has the opposite sign. */
  private Double margin;

  private Integer analysisVersion;
}
