package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
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

  public FightDetector.Tuning fightTuning() {
    return new FightDetector.Tuning(
        fightMergeGapSeconds,
        fightMinDamagePerSide,
        fightMinHeroesPerSide,
        fightIntensityWindowSeconds,
        fightIntensityMinDamage);
  }

  public ActivityTuning activityTuning() {
    return new ActivityTuning(
        activityFarmLookbackSeconds, activityTravelUnitsPerSecond, activityJumpUnitsPerSecond);
  }
}
