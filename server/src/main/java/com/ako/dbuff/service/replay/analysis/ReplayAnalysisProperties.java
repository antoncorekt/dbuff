package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import com.ako.dbuff.service.replay.analysis.death.DeathAssessor;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.spike.SpikeDetector;
import com.ako.dbuff.service.replay.analysis.team.TeamStateSegmenter;
import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.*}: the thresholds the analyzers' rules use. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis")
public class ReplayAnalysisProperties {
  private static final int MAX_INCOME_BREAK_MINUTES = 30;
  private static final int MAX_PERCENT = 100;
  private static final int MAX_STRETCH_MINUTES = 240;
  private static final double MAX_INCOME_BREAK_SHARE = 5;
  private static final Pattern ITEM_NAME = Pattern.compile("item_[a-z0-9]+(_[a-z0-9]+)*");

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

  private FightAbility fightAbility = new FightAbility();

  /** Bound from {@code replay.analysis.fight-ability.*}. */
  @Data
  public static class FightAbility {
    /** The built-in items never meant to be cast in a fight; not configurable, only extended. */
    static final Set<String> DEFAULT_IGNORED_ITEMS =
        Set.of(
            "item_quelling_blade",
            "item_magic_stick",
            "item_soul_ring",
            "item_hand_of_midas",
            "item_echo_sabre",
            "item_branches",
            "item_bfury",
            "item_tango",
            "item_flask",
            "item_clarity",
            "item_faerie_fire",
            "item_enchanted_mango",
            "item_ward_observer",
            "item_ward_sentry",
            "item_ward_dispenser",
            "item_tpscroll",
            "item_travel_boots",
            "item_travel_boots_2",
            "item_bottle",
            "item_cheese",
            "item_smoke_of_deceit",
            "item_power_treads",
            "item_tranquil_boots",
            "item_ring_of_basilius");

    /** Items added to the built-in ones, so a setting can never bring a consumable back. */
    private List<String> extraIgnoredItems = List.of();
  }

  /** Fails at startup on an income break setting that could not tell a break from noise. */
  @PostConstruct
  void validate() {
    requireRange("outlier-share-percent", outlierSharePercent, 1, MAX_PERCENT);
    requireRange("outlier-minutes", outlierMinutes, 1, MAX_STRETCH_MINUTES);
    requireRange("feeder-share-percent", feederSharePercent, 1, MAX_PERCENT);
    requireRange("feeder-minutes", feederMinutes, 1, MAX_STRETCH_MINUTES);
    requireRange("income-break-minutes", incomeBreakMinutes, 1, MAX_INCOME_BREAK_MINUTES);
    requireItemNames(fightAbility.getExtraIgnoredItems());
    if (!(incomeBreakShare > 0 && incomeBreakShare <= MAX_INCOME_BREAK_SHARE)) {
      throw new IllegalStateException(
          "replay.analysis.income-break-share must be above 0 and at most "
              + MAX_INCOME_BREAK_SHARE
              + ", got "
              + incomeBreakShare);
    }
  }

  private static void requireItemNames(List<String> items) {
    if (items == null) {
      throw new IllegalStateException(
          "replay.analysis.fight-ability.extra-ignored-items must be set");
    }
    for (String item : items) {
      if (item == null || !ITEM_NAME.matcher(item).matches()) {
        throw new IllegalStateException(
            "replay.analysis.fight-ability.extra-ignored-items must hold item names in lower snake"
                + " case starting with item_, got "
                + item);
      }
    }
  }

  private static void requireRange(String name, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalStateException(
          "replay.analysis." + name + " must be " + min + " to " + max + ", got " + value);
    }
  }

  public Set<String> fightAbilityIgnoredItems() {
    Set<String> items = new HashSet<>(FightAbility.DEFAULT_IGNORED_ITEMS);
    items.addAll(fightAbility.getExtraIgnoredItems());
    return Set.copyOf(items);
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
