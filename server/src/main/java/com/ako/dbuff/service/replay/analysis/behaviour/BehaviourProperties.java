package com.ako.dbuff.service.replay.analysis.behaviour;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.behaviour.*}; first-guess values for the review rounds. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.behaviour")
public class BehaviourProperties {
  /** Heroes of a team within this many world units of each other are one group. */
  private int clusterDistance = 2500;

  /** A group needs at least this many heroes; with fewer nobody counts as away. */
  private int minClusterSize = 2;

  /** A hero away from the group at least this long is on a trip. */
  private int awaySeconds = 20;

  /** The creep kills of a lane are compared this long after its hero left. */
  private int proxyWindowSeconds = 60;

  /** A kill or fight this long after a trip ended still belongs to it. */
  private int outcomeLingerSeconds = 15;

  /** The income of a trip is read this long after it ended too, to catch the walk back. */
  private int incomeTailSeconds = 60;

  /** A trip whose income per minute fell to this share of the one before it, or lower, dropped. */
  private double incomeShare = 0.7;

  /** When no lane result exists, the group-or-split reading starts at this game second. */
  private int splitFallbackStartSeconds = 720;

  /** A team is grouped when this many of its living heroes (or all of them, if fewer) are close. */
  private int groupedMinHeroes = 4;

  /** A team is split when this many living heroes each stand alone, away from every teammate. */
  private int splitMinAloneHeroes = 2;

  /** A team with fewer living heroes than this at a sample gets no label for it. */
  private int postureMinLivingHeroes = 4;

  /**
   * A stretch shorter than this many minutes is absorbed by its neighbour, so labels do not flap.
   */
  private int minStretchMinutes = 2;

  /** The lane swap compares the stored lane with where the hero stood from this minute. */
  private int swapFromMinute = 8;

  /** The lane swap window ends at the start of this minute. */
  private int swapToMinute = 12;

  /** A hero has a main lane in the window only if he was in it this share of his living time. */
  private double swapMinShare = 0.5;

  /** Heroes that got the smoke modifier this close to a use, on the user's team, were in it. */
  private int smokeGroupSeconds = 3;

  /** A smoke modifier removed this much before its full duration ran out was broken. */
  private double smokeExpirySlackSeconds = 1.0;

  /** A smoke is judged by what its group did from its start to this long after its end. */
  private int smokeOutcomeSeconds = 20;

  /** A won important fight counts as converted when an objective falls this long after it. */
  private int objectiveWindowSeconds = 90;

  /** Heroes of the winner this close to the hero who killed Roshan were at the pit. */
  private int roshanNearRadius = 1500;

  @PostConstruct
  void validate() {
    require(minClusterSize >= 2, "min-cluster-size must be at least 2, not " + minClusterSize);
    require(
        splitFallbackStartSeconds >= 0,
        "split-fallback-start-seconds must not be negative, not " + splitFallbackStartSeconds);
    require(
        groupedMinHeroes >= 2 && groupedMinHeroes <= 5,
        "grouped-min-heroes must be 2 to 5, not " + groupedMinHeroes);
    require(
        groupedMinHeroes >= minClusterSize,
        "grouped-min-heroes ("
            + groupedMinHeroes
            + ") must be at least min-cluster-size ("
            + minClusterSize
            + ")");
    require(
        postureMinLivingHeroes >= 2 && postureMinLivingHeroes <= 5,
        "posture-min-living-heroes must be 2 to 5, not " + postureMinLivingHeroes);
    require(
        splitMinAloneHeroes >= 2 && splitMinAloneHeroes <= 5,
        "split-min-alone-heroes must be 2 to 5, not " + splitMinAloneHeroes);
    require(
        minStretchMinutes >= 1, "min-stretch-minutes must be at least 1, not " + minStretchMinutes);
    require(
        swapFromMinute >= 0 && swapFromMinute < swapToMinute,
        "swap-from-minute must be below swap-to-minute, not "
            + swapFromMinute
            + " and "
            + swapToMinute);
    require(
        swapMinShare > 0 && swapMinShare <= 1,
        "swap-min-share must be in (0, 1], not " + swapMinShare);
    require(
        smokeGroupSeconds >= 0,
        "smoke-group-seconds must not be negative, not " + smokeGroupSeconds);
    require(
        smokeExpirySlackSeconds >= 0,
        "smoke-expiry-slack-seconds must not be negative, not " + smokeExpirySlackSeconds);
    require(
        smokeOutcomeSeconds >= 1,
        "smoke-outcome-seconds must be at least 1, not " + smokeOutcomeSeconds);
    require(
        objectiveWindowSeconds >= 1,
        "objective-window-seconds must be at least 1, not " + objectiveWindowSeconds);
    require(
        roshanNearRadius >= 1, "roshan-near-radius must be at least 1, not " + roshanNearRadius);
  }

  private static void require(boolean ok, String message) {
    if (!ok) {
      throw new IllegalStateException("replay.analysis.behaviour." + message);
    }
  }
}
