package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** Per-minute gold, xp, last hit and deny totals for one player. */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "gameMinute"}),
    indexes = @Index(name = "idx_player_timeline_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerTimelineDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_timeline_seq")
  @SequenceGenerator(
      name = "player_timeline_seq",
      sequenceName = "player_timeline_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  /** Account id, -1 for anonymous players. */
  private Long playerId;
  private Integer gameMinute;
  private Long gold;
  private Long xp;
  private Long lastHits;
  private Long denies;
  /** Series below are absent from older parser versions and stay null then. */
  private Long networth;
  private Long heroDamage;
  private Long towerDamage;
  private Long heroHealing;
  private Long campsStacked;
}
