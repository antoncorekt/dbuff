package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import com.ako.dbuff.service.replay.analysis.death.DeathAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.*}: the thresholds the analyzers' rules use. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis")
public class ReplayAnalysisProperties {
  /** A creep last hit up to this long before a sample, or during it, counts the hero as farming. */
  private int activityFarmLookbackSeconds = 10;

  /** Average speed between two samples above which a hero without farming is travelling. */
  private int activityTravelUnitsPerSecond = 200;

  /** A speed above this is a teleport or respawn jump, which is not travelling on foot. */
  private int activityJumpUnitsPerSecond = 700;

  /** Hostile events closer together than this belong to one fight. */
  private int fightMergeGapSeconds = 10;

  /** A fight without a death needs this much hero damage from each side to count. */
  private int fightMinDamagePerSide = 600;

  /** A fight without a death needs at least this many heroes on each side. */
  private int fightMinHeroesPerSide = 2;

  /** Hero damage within a few seconds that makes damage fight activity, not a poke. */
  private int fightIntensityWindowSeconds = 5;

  private int fightIntensityMinDamage = 400;

  /** A hero this close to the fight at any sample during it was present, even if idle. */
  private int fightParticipationRadius = 1500;

  /** The share of the total, of level or damage, by which one side must lead to win a fight. */
  private int fightWinMarginPercent = 10;

  /** How far before a death damage, disables and BKB use are looked at. */
  private int deathLookbackSeconds = 8;

  /** Damage in the last three seconds from which a death is called a burst. */
  private int deathBurstDamage = 1500;

  /** A hero with no ally and at least two enemies this close was caught out. */
  private int deathIsolationRadius = 1500;

  /** Seconds of disables in the lookback from which a death is called a disable chain. */
  private int deathDisableSeconds = 2;

  /** A hero alive this long after dying came back by Aegis or reincarnation. */
  private int deathReincarnationSeconds = 5;

  /** A hero who dies this soon into a fight is said to have had no time to use what he held. */
  private int fightEarlyDeathSeconds = 5;

  /** A hero this many places further forward than his attack range suggests was out of position. */
  private int deathOutOfPositionRanks = 2;

  /** A team is ahead from this share of both teams' net worth together, in percent. */
  private int teamAheadPercent = 5;

  /** A lead must hold this long before the team counts as ahead or behind. */
  private int teamStateEnterSeconds = 120;

  /** A lead must have faded this long before the team counts as even again. */
  private int teamStateLeaveSeconds = 60;

  public TeamStateSegmenter.Tuning teamStateTuning() {
    return new TeamStateSegmenter.Tuning(
        Math.max(1, teamAheadPercent) / 100.0,
        Math.max(1, teamStateEnterSeconds),
        Math.max(1, teamStateLeaveSeconds));
  }

  public FightDetector.Tuning fightTuning() {
    return new FightDetector.Tuning(
        fightMergeGapSeconds,
        fightMinDamagePerSide,
        fightMinHeroesPerSide,
        fightIntensityWindowSeconds,
        fightIntensityMinDamage);
  }

  public DeathAssessor.Tuning deathTuning() {
    return new DeathAssessor.Tuning(
        deathLookbackSeconds,
        deathBurstDamage,
        deathIsolationRadius,
        deathDisableSeconds,
        deathReincarnationSeconds,
        Math.max(1, deathOutOfPositionRanks));
  }

  public ActivityTuning activityTuning() {
    return new ActivityTuning(
        activityFarmLookbackSeconds, activityTravelUnitsPerSecond, activityJumpUnitsPerSecond);
  }
}
