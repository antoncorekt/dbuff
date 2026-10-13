package com.ako.dbuff.service.replay.analysis.lane;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.lanes.*}; every number is a first guess for the review rounds.
 * The lane end is found from signals, the cap is only a safety limit.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.lanes")
public class LaneProperties {
  /** A hero's lane is the lane zone he spent most time in from this second ... */
  private int assignFromSeconds = 0;

  /** ... to this second. */
  private int assignToSeconds = 240;

  /** The share of his time alive in that span a hero must spend in one lane to have a lane. */
  private double minLaneShare = 0.4;

  /** Away from the lane farming the jungle or another lane this long ends the hero's laning. */
  private int awaySeconds = 30;

  /**
   * Away from the lane for any reason (a teleport, the base) this long without a return ends it.
   */
  private int returnSeconds = 60;

  /** The first seconds after a respawn, the walk back from the fountain, do not count as away. */
  private int respawnGraceSeconds = 45;

  /** The safety limit of the laning stage, in game seconds. */
  private int capSeconds = 900;

  /** A tower of this tier in the lane falling ends the laning of the whole lane. */
  private int towerTier = 1;

  /**
   * A hero who stays after every hero of the other side has left is no longer laning against
   * anyone: his window ends there, so his solo farming does not count as winning the lane.
   */
  private boolean endWhenOpponentsLeft = true;

  /** The experience of a creep counts this much against its gold in the creep score. */
  private double xpWeight = 0.5;

  /** A deny is scored by the experience it takes from the enemy ... */
  private double denyXpWeight = 0.5;

  /** ... and, less, by the gold it takes. */
  private double denyGoldWeight = 0.25;

  /** The weights of the lane result: each term is a difference scaled to -1 .. 1. */
  private double creepWeight = 0.35;

  private double xpGainWeight = 0.25;
  private double levelWeight = 0.15;
  private double killWeight = 0.15;
  private double denyWeight = 0.10;

  /** A level lead this big counts as a full lead. */
  private double levelFullLead = 2.0;

  /** A kill lead this big counts as a full lead. */
  private double killFullLead = 3.0;

  /** Denies, in a lane, this far apart count as a full lead. */
  private double denyFullLead = 6.0;

  /** Added to the side whose lane tower stood when the other side's fell during the laning. */
  private double towerBonus = 0.25;

  /** A margin below this is EVEN. */
  private double evenBand = 0.10;

  /** A reason below this severity is not kept. */
  private double minSeverity = 0.1;

  /** A partner who left or arrived this long apart from the hero is a reason. */
  private int partnerGapSeconds = 60;

  /** Hero damage an outside enemy hero did to the lane before it reads as a gank. */
  private int gankDamage = 500;

  /** Hero damage taken beyond the damage dealt that reads as harassment. */
  private int harassmentDamage = 300;

  /** A kill or a death before this second is early. */
  private int earlyKillSeconds = 300;

  /** Siege last hits or denies this far apart are an edge. */
  private int siegeEdge = 1;

  /** Denies this far apart are an edge. */
  private int denyEdge = 3;

  /** The reasons kept per side of a lane. */
  private int reasonsPerSide = 6;
}
