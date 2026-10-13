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
 * One rune a hero picked up, with where he was, the bounty gold the combat log paid for it and
 * whether he was a mid laner at the time. The mid lane flag is a proxy until the lane analysis
 * exists.
 */
@Entity
@Table(
    name = "rune_pickup",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "runeIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunePickupDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rune_pickup_seq")
  @SequenceGenerator(
      name = "rune_pickup_seq",
      sequenceName = "rune_pickup_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** RADIANT or DIRE. */
  private String team;

  /** The pickup's order in the player's rune log. */
  private Integer runeIndex;

  /** One of the RuneType values. */
  private String runeType;

  /** Game seconds; negative before the horn. */
  private Integer seconds;

  /** BASE, LANE, RIVER or JUNGLE at the pickup; null without a track or a frozen map layout. */
  private String zone;

  private String lane;

  /**
   * The taker's own bounty gold entry (each hero of his team gets the same); null for other runes
   * and without a gold reason.
   */
  private Integer gold;

  private Boolean midLane;
  private Integer analysisVersion;
}
