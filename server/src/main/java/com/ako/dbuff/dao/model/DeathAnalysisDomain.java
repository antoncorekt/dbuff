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
 * What the replay shows about one hero death: where, by whom, what the hero had to survive it, and
 * the first thing that explains it. It adds to {@link DeathEventDomain}, which has OpenDota's gold
 * and respawn figures for the same death; the two are matched by player and time, because the
 * combat log also records an Aegis use as a death and OpenDota does not.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "deathIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeathAnalysisDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "death_analysis_seq")
  @SequenceGenerator(
      name = "death_analysis_seq",
      sequenceName = "death_analysis_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** 0-based, over every death the combat log records for the hero, an Aegis use included. */
  private Integer deathIndex;

  private Double deathSeconds;

  /** HERO, CREEP, NEUTRAL, TOWER or OTHER. */
  private String killerKind;

  private String killerName;
  private Integer assists;
  private Integer x;
  private Integer y;

  /** BASE, LANE, RIVER or JUNGLE; null without a map. */
  private String zone;

  private String lane;

  /** The fight this death belongs to; null for a death outside any fight. */
  private Integer fightIndex;

  private Boolean isolated;

  /**
   * The hero's frontline delta when the fight he died in began (negative: further forward than his
   * range suggests), not where he stood when he died.
   */
  private Integer frontlineDelta;

  private Boolean reincarnated;
  private Boolean bkbOwned;

  /** Ready eight seconds before the death; only meaningful when owned. */
  private Boolean bkbReady;

  private Boolean bkbActive;
  private Boolean bkbExpiredJustBefore;
  private Double disableSeconds;
  private Long damageLookback;
  private Long damageLastThreeSeconds;

  /** The biggest damage sources, as name:damage. */
  private String topSources;

  /** Escape and save items held, comma separated. */
  private String saveItemsOwned;

  private Boolean teleportReady;

  /** One of the DeathReason values. */
  private String reason;

  private Integer analysisVersion;
}
