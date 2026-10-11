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

  private Integer analysisVersion;
}
