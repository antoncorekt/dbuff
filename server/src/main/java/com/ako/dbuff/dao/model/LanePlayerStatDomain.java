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

/** What one hero did inside his laning window: creeps, experience, fights and damage. */
@Entity
@Table(
    name = "lane_player_stat",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}),
    indexes = @Index(name = "idx_lane_player_stat_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanePlayerStatDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lane_player_stat_seq")
  @SequenceGenerator(
      name = "lane_player_stat_seq",
      sequenceName = "lane_player_stat_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Integer pairId;

  /** All last hits and denies inside his window. */
  private Integer lastHits;

  private Integer meleeLastHits;
  private Integer rangedLastHits;
  private Integer siegeLastHits;
  private Integer denies;

  /** The value-weighted score of his last hits and denies. */
  private Double creepScore;

  /** His share of the creep score of his side of the lane; 1 when he was alone. */
  private Double sideShare;

  /** Experience and gold gained in the window, from the per minute series; null without it. */
  private Double xp;

  private Double gold;

  /** His level when the window ended; null when the track does not say. */
  private Integer level;

  private Integer kills;
  private Integer deaths;
  private Long heroDamageDealt;
  private Long heroDamageTaken;
  private Long towerDamage;
  private Integer stacks;
  private Integer analysisVersion;
}
