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

/** How one player took part in one fight, or why they did not. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex", "playerSlot"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightPlayerDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_player_seq")
  @SequenceGenerator(
      name = "fight_player_seq",
      sequenceName = "fight_player_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;
  private Long playerSlot;

  /** One of the Presence values. */
  private String presence;

  private Long damageDealt;
  private Long damageTaken;
  private Integer deaths;

  /** Distance to the centre of the fight at its start; null when the hero had no position. */
  private Integer distanceToCenter;

  /** What the activity classifier says the hero was doing when the fight began. */
  private String activityAtStart;

  /** One of the AbsenceReason values; null unless the hero was alive and away from the fight. */
  private String absenceReason;

  /** Seconds until the quickest teleport he held was ready; null when he held none. */
  private Integer teleportCooldownLeft;

  /** Attack range at the start of the fight; null when the track has none. */
  private Integer attackRange;

  /** 1 is the hero of those taking part who stood closest to the enemy; null for the others. */
  private Integer frontlineRank;

  /**
   * Rank minus the rank his attack range suggests (short range in front); negative means he stood
   * further forward than his range suggests. Null when ranges are unknown.
   */
  private Integer frontlineDelta;

  private Integer analysisVersion;
}
