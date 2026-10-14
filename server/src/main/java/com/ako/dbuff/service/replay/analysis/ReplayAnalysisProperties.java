package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import com.ako.dbuff.service.replay.analysis.death.DeathAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.spike.SpikeDetector;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.*}: the thresholds the analyzers' rules use. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis")
public class ReplayAnalysisProperties {
  private static final int MAX_INCOME_BREAK_MINUTES = 30;
  private static final double MAX_INCOME_BREAK_SHARE = 5;

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

  /** Before this game second a death with no BKB is not blamed on it; BKB costs 4050 gold. */
  private int deathNoBkbMinSeconds = 720;

  /** A team is ahead from this share of both teams' net worth together, in percent. */
  private int teamAheadPercent = 5;

  /** A lead must hold this long before the team counts as ahead or behind. */
  private int teamStateEnterSeconds = 120;

  /** A lead must have faded this long before the team counts as even again. */
  private int teamStateLeaveSeconds = 60;

  /** The laning phase ends at this game minute. */
  private int phaseLaneEndMinutes = 12;

  /** The mid game ends at this game minute; the late game follows. */
  private int phaseMidEndMinutes = 25;

  /** A swing is at least this much net worth. */
  private int spikeMinGold = 1500;

  /** A swing is at least this share of both teams' net worth together, in percent. */
  private int spikeMinPercent = 3;

  /** A swing takes up to this many minutes to start; it goes on while it keeps moving. */
  private int spikeMaxMinutes = 3;

  /** A lead of this size on both sides of a swing makes it a change of direction. */
  private int spikeFlipGold = 1000;

  /** A source must make up this share of a swing to be named as its cause, in percent. */
  private int spikeMinSharePercent = 10;

  /** A player holding more than this share of his team's net worth, in percent, stands out. */
  private int outlierSharePercent = 35;

  /** ...for at least this many minutes. */
  private int outlierMinutes = 3;

  /** A player holding less than this share, in percent, and dying a lot, is feeding. */
  private int feederSharePercent = 10;

  /** ...for at least this many minutes. */
  private int feederMinutes = 5;

  /**
   * The income breaks from his team's when his gain per minute differs from theirs for this long.
   */
  private int incomeBreakMinutes = 3;

  /** ...by at least this share of the teammates' average gain per minute. */
  private double incomeBreakShare = 0.25;

  /** Fails at startup on an income break setting that could not tell a break from noise. */
  @PostConstruct
  void validate() {
    if (incomeBreakMinutes < 1 || incomeBreakMinutes > MAX_INCOME_BREAK_MINUTES) {
      throw new IllegalStateException(
          "replay.analysis.income-break-minutes must be 1 to "
              + MAX_INCOME_BREAK_MINUTES
              + ", got "
              + incomeBreakMinutes);
    }
    if (!(incomeBreakShare > 0 && incomeBreakShare <= MAX_INCOME_BREAK_SHARE)) {
      throw new IllegalStateException(
          "replay.analysis.income-break-share must be above 0 and at most "
              + MAX_INCOME_BREAK_SHARE
              + ", got "
              + incomeBreakShare);
    }
  }

  public SpikeDetector.Tuning spikeTuning() {
    return new SpikeDetector.Tuning(
        Math.max(1, spikeMinGold),
        Math.max(1, spikeMinPercent) / 100.0,
        Math.max(1, spikeMaxMinutes),
        spikeFlipGold);
  }

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
        Math.max(1, deathOutOfPositionRanks),
        deathNoBkbMinSeconds);
  }

  public ActivityTuning activityTuning() {
    return new ActivityTuning(
        activityFarmLookbackSeconds, activityTravelUnitsPerSecond, activityJumpUnitsPerSecond);
  }
}
