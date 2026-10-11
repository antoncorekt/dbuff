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
 * Cumulative counters of one player at a game minute, as the replay keeps them. Only what
 * PlayerTimelineDomain does not already hold; a counter the replay lacks is null.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "gameMinute"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayPlayerMinuteDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_player_minute_seq")
  @SequenceGenerator(
      name = "replay_player_minute_seq",
      sequenceName = "replay_player_minute_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** Minute of game time; -1 is the minute before the horn. */
  private Integer gameMinute;

  private Long heroKillGold;
  private Long creepKillGold;
  private Long neutralKillGold;
  private Long bountyGold;
  private Long roshanGold;
  private Long buildingGold;
  private Long courierGold;
  private Long comebackGold;
  private Long incomeGold;
  private Long sharedGold;
  private Long wardKillGold;
  private Long creepDenyGold;
  private Long otherGold;
  private Long reliableGold;
  private Long unreliableGold;
  private Long nearbyCreepDeaths;
  private Long missCount;
  private Long claimedMissCount;
  private Long obsPlaced;
  private Long sentPlaced;
  private Long wardsDestroyed;
  private Long creepsStacked;
  private Long campsStacked;
  private Long goldLostToDeath;
  private Long goldSpentOnBuybacks;
  private Long courierKills;
  private Long kills;
  private Long deaths;
  private Long assists;
  private Long bountyRunes;
  private Long powerRunes;
  private Long waterRunes;
}
