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
 * What the winner of an important fight did with it: took a tower, barracks, the ancient or Roshan
 * in the window after the fight, or missed, with the enemy tower nearest to its heroes.
 */
@Entity
@Table(
    name = "objective_conversion",
    indexes = @Index(name = "idx_objective_conversion_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectiveConversionDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "objective_conversion_seq")
  @SequenceGenerator(
      name = "objective_conversion_seq",
      sequenceName = "objective_conversion_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;

  /** RADIANT or DIRE. */
  private String winnerTeam;

  /** CONVERTED or MISSED. */
  private String result;

  /** TOWER, BARRACKS, ANCIENT or ROSHAN; null when MISSED. */
  private String objectiveKind;

  /** The combat log name of the building, or of Roshan; null when MISSED. */
  private String objectiveKey;

  /** From the fight's end to the objective; 0 when it fell inside the fight; null when MISSED. */
  private Integer secondsAfter;

  /** The winner's net worth state at the fight's start: AHEAD, EVEN or BEHIND; null if unknown. */
  private String stateBefore;

  /** Heroes of the winner alive at the fight's end. */
  private Integer winnersAlive;

  /** The enemy lane tower still standing closest to a living winner hero at the fight's end. */
  private String nearestTower;

  private String towerLane;

  /** Map units from that tower to the winner's closest living hero. */
  private Integer towerDistance;

  private Long nearestHeroSlot;
  private String heroZone;
  private String heroLane;

  /** The winner's heroes within the Roshan radius of the killer, comma separated slots. */
  private String roshanNearSlots;

  /** EXPECTED, GAMBLE or EVEN for a Roshan kill; null otherwise or when the state is unknown. */
  private String roshanCall;

  private Integer analysisVersion;
}
