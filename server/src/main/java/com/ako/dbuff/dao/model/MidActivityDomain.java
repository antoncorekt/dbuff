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
 * What the mid hero (the final role 2) of a team did once the lane was over, with the numbers
 * behind the verdict. Gold and experience per minute are those of the mid-game window.
 */
@Entity
@Table(
    name = "mid_activity",
    indexes = @Index(name = "idx_mid_activity_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MidActivityDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mid_activity_seq")
  @SequenceGenerator(
      name = "mid_activity_seq",
      sequenceName = "mid_activity_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** RADIANT or DIRE. */
  private String team;

  /** When the track first shows level 6; null when he never reached it or the track is missing. */
  private Integer level6Seconds;

  /** The highest level the track shows; null when the hero has no track. */
  private Integer maxLevel;

  /** One of the MidNext values; null without a level 6 time. */
  private String nextAction;

  /** When he left mid or joined the fight; null for STAYED_MID. */
  private Integer leaveSeconds;

  /** The lane he went to, or JUNGLE; null for STAYED_MID and FIGHT. */
  private String nextPlace;

  /** Kills in the kill window after {@code leaveSeconds}; null for STAYED_MID. */
  private Integer killsAfterLeaving;

  private Integer windowStartSeconds;
  private Integer windowEndSeconds;
  private Integer kills;
  private Integer assists;

  /** Fights of the window he took part in, all fights of the window, and his team's median. */
  private Integer fightsJoined;

  private Integer fightsTotal;
  private Double teamMedianFights;

  /** His team rotations that started in the window, and those whose verdict was GOOD. */
  private Integer rotations;

  private Integer rotationsGood;

  /** Per minute over the window; null when the timeline does not cover it. */
  private Integer goldPerMinute;

  private Integer xpPerMinute;

  /** The enemy mid; null when the enemy team had no role 2. */
  private Long enemyMidSlot;

  private Integer enemyGoldPerMinute;
  private Integer enemyXpPerMinute;

  /** The average of his five-hero team, himself included. */
  private Integer teamGoldPerMinute;

  private Integer teamXpPerMinute;

  /** ACTIVE or PASSIVE. */
  private String verdict;

  private Integer analysisVersion;
}
