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
 * One use of a Smoke of Deceit: who used it, who was in the group, where the group stood when it
 * started and ended, how it ended and what came of it.
 */
@Entity
@Table(name = "smoke_use", indexes = @Index(name = "idx_smoke_use_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmokeUseDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "smoke_use_seq")
  @SequenceGenerator(name = "smoke_use_seq", sequenceName = "smoke_use_seq", allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Long userSlot;

  /** The slots of the heroes in the group, the user included, comma separated. */
  private String groupSlots;

  /** Game seconds. */
  private Integer startSeconds;

  /** When the smoke broke for the first hero of the group, else when the last modifier ran out. */
  private Integer endSeconds;

  /** EXPIRED or BROKEN. */
  private String endReason;

  /** BASE, LANE, RIVER or JUNGLE where the group stood; null without a track sample. */
  private String startZone;

  /** TOP, MID or BOTTOM for a lane zone. */
  private String startLane;

  private String endZone;
  private String endLane;

  /** One of the SmokeOutcome values. */
  private String outcome;

  /** Enemy hero deaths the group killed or assisted in, from the start to the end of the window. */
  private Integer kills;

  /** Enemy towers, barracks or ancients that fell in the window; a deny is not counted. */
  private Integer buildings;

  private Integer roshanKills;

  /** Deaths of heroes of the group in the window. */
  private Integer groupDeaths;

  /** Deaths of enemy heroes in the window, whoever killed them. */
  private Integer enemyDeaths;

  private Integer analysisVersion;
}
