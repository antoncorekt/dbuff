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
 * A skill the enemy took later in the Ability Draft that did clearly more in the match than the one
 * a player took first, while it was still in the pool.
 */
@Entity
@Table(
    name = "missed_pick",
    indexes = @Index(name = "idx_missed_pick_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissedPickDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "missed_pick_seq")
  @SequenceGenerator(
      name = "missed_pick_seq",
      sequenceName = "missed_pick_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** The player who took the skill. */
  private Long playerSlot;

  private String takenAbility;

  /** 1 to 50 over the skill and hero turns of the draft. */
  private Integer takenTurn;

  private String candidateAbility;
  private Integer candidateTurn;

  /** The enemy player who took the candidate. */
  private Long candidateOwnerSlot;

  /** Both skills hold the ultimate slot, or both a regular one. */
  private Boolean ultimate;

  /** DAMAGE (the candidate did more total damage) or FIGHTS (neither did damage). */
  private String basis;

  private Long takenDamage;
  private Long candidateDamage;

  /** Candidate damage over the taken skill's; null when the taken skill did none. */
  private Double ratio;

  /** Important fights the enemy won in which the candidate was cast. */
  private Integer candidateWonFights;

  /** Important fights the enemy won in all. */
  private Integer enemyWonFights;

  /** Important fights the player's team won in which the taken skill was cast. */
  private Integer takenWonFights;

  private Integer analysisVersion;
}
