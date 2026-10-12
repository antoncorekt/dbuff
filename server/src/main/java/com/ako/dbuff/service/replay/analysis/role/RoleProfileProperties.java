package com.ako.dbuff.service.replay.analysis.role;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.role-profile.*}: every weight, scale and key list of the role
 * model. All of it is a hypothesis to be changed by the learning loop, so none of it is written in
 * the code that uses it.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.role-profile")
public class RoleProfileProperties {
  private Intent intent = new Intent();
  private Observed observed = new Observed();
  private Fit fit = new Fit();

  /** Fails at startup, with the missing entry named, when an override left a hole in the tables. */
  @PostConstruct
  public void validate() {
    for (double unknown : new double[] {fit.getUnknownValue(), fit.getFinalUnknownValue()}) {
      if (unknown < 0 || unknown > 1) {
        throw new IllegalStateException(
            "replay.analysis.role-profile.fit unknown-value and final-unknown-value must be in"
                + " 0 to 1, got "
                + unknown);
      }
    }
    double[] fullShares = {
      observed.getDamageFullShare(),
      observed.getHealFullShare(),
      observed.getDamageTakenFullShare(),
      observed.getWardFullShare(),
      observed.getLastHitFullShare()
    };
    for (double share : fullShares) {
      if (!(share > 0)) {
        throw new IllegalStateException(
            "replay.analysis.role-profile.observed full shares must be above 0, got " + share);
      }
    }
    for (int role = 1; role <= RoleFitter.ROLES; role++) {
      Map<RoleFunction, Double> weights = fit.getWeights().get(role);
      for (RoleFunction function : RoleFunction.values()) {
        if (weights == null || weights.get(function) == null) {
          throw new IllegalStateException(
              "replay.analysis.role-profile.fit.weights has no value for role "
                  + role
                  + " and function "
                  + function);
        }
      }
      Map<LanePosition, Double> lanes = fit.getLaneBonus().get(role);
      for (LanePosition lane : LanePosition.values()) {
        if (lanes == null || lanes.get(lane) == null) {
          throw new IllegalStateException(
              "replay.analysis.role-profile.fit.lane-bonus has no value for role "
                  + role
                  + " and lane "
                  + lane);
        }
      }
    }
  }

  /** How the hero and his skills become a profile; a sum over skills is cut at 1. */
  @Data
  public static class Intent {
    /** A skill counts this much, an ultimate counts {@code ultimateWeight}. */
    private double skillWeight = 1.0;

    private double ultimateWeight = 1.5;

    /** A cooldown shorter than this counts as this, so a near-free skill does not dominate. */
    private double cooldownFloorSeconds = 5;

    /**
     * Attribute keys, by their ending, that hold seconds of control on an enemy. A plain {@code
     * channel_time} is not here: a self-channel such as Teleport is not control.
     */
    private List<String> disableKeySuffixes =
        List.of(
            "stun_duration",
            "silence_duration",
            "root_duration",
            "sleep_duration",
            "hex_duration",
            "fear_duration",
            "taunt_duration");

    /**
     * Keys that hold seconds of control only on a skill that targets one enemy unit; a channel on
     * an enemy holds him, a channel on oneself does not.
     */
    private List<String> enemyUnitDisableKeys = List.of("duration", "channel_time");

    /** ...and a longer plain duration is a debuff, not a lockdown, and does not count. */
    private double enemyUnitDisableMaxSeconds = 6;

    /** A disable that goes through BKB counts this many times more. */
    private double bkbPierceFactor = 1.5;

    /** Seconds of control per second of cooldown, summed over the skills, that make a 1. */
    private double disableFull = 1.0;

    /** Area or no-target control per second of cooldown, summed, that makes an initiation of 1. */
    private double initiationFull = 1.0;

    /** An area or no-target disable counts for initiation with this share of its disable value. */
    private double initiationDisableShare = 1.0;

    /** Attribute keys of a gap closer, a skill that takes the hero to the enemy in one step. */
    private List<String> gapCloserKeys =
        List.of("min_blink_range", "blink_range", "max_travel_distance", "leap_distance");

    /**
     * A stun, hex, root, sleep, fear, taunt or silence of at least {@code initiationMinControl}
     * seconds on a skill that reaches at least {@code initiationMinCastRange} (read from {@code
     * initiationRangeKeys}) or hits an area is an initiation, whatever its cooldown; each such
     * skill adds {@code initiationSkill}.
     */
    private List<String> initiationRangeKeys = List.of("abilitycastrange");

    private double initiationMinCastRange = 400;
    private double initiationMinControl = 1.0;
    private double initiationSkill = 0.5;

    /** A gap closer adds this to initiation, and to roaming as a mobility skill. */
    private double gapCloserInitiation = 0.4;

    /** Attribute keys of other mobility: invisibility and bonus movement speed. */
    private List<String> mobilityKeys =
        List.of("fade_time", "invis_duration", "bonus_movement_speed", "bonus_movespeed");

    /** Mobility skills, summed, that make a roaming of 1. */
    private double roamingFull = 2.0;

    /** A skill with disable or damage that costs at most this much mana at level 1 is cheap... */
    private double cheapManaCost = 100;

    /** ...and has at most this cooldown at its top level. */
    private double cheapMaxCooldown = 14;

    /** A cheap disable or nuke counts this much towards roaming. */
    private double cheapSkillRoaming = 0.5;

    /** Attribute keys that hold the damage of a skill. */
    private List<String> damageKeys =
        List.of("damage", "nova_damage", "total_damage", "damage_per_second");

    /**
     * Attribute keys of a skill that makes the hero's attacks stronger or faster: a positive value
     * counts. A skill that is an attack modifier, or a passive that deals damage, counts as well.
     */
    private List<String> attackDamageKeys =
        List.of(
            "bonus_attack_speed",
            "attack_speed",
            "bonus_damage",
            "illusion_outgoing_tooltip",
            "gods_strength_damage",
            "great_cleave_damage",
            "distance_damage_pct",
            "katana_bleed_attack_damage_pct",
            "wolf_damage");

    private List<String> attackBehaviors = List.of("Attack Modifier");

    /** An attack skill adds this to damage... */
    private double attackSkillDamage = 0.2;

    /** ...and this to farm priority. */
    private double attackSkillFarm = 0.15;

    /** Skill damage per second of cooldown, summed over the skills, that makes a 1. */
    private double damageFull = 60;

    /** The attack damage of the hero that counts for a 1, and the share of the value it fills. */
    private double attackDamageFull = 60;

    private double attackDamageShare = 0.3;

    /**
     * Attribute keys that mark a skill as healing, shielding or reducing damage for an ally. The
     * skill must target a friendly unit; the constants say nothing about a skill with no target.
     */
    private List<String> saveKeys =
        List.of(
            "heal",
            "heal_amount",
            "heal_pct",
            "base_heal",
            "hp_heal",
            "heal_per_second",
            "damage_reduction",
            "damage_block",
            "damage_block_base",
            "barrier",
            "barrier_amount",
            "shield_pct_absorb",
            "base_shield",
            "damage_absorb",
            "shield_amount");

    /**
     * Keys of a no-target skill that heals the allies around it. Block and barrier keys are not
     * here: on a no-target skill they are as likely to protect only the caster.
     */
    private List<String> areaSaveKeys =
        List.of("heal", "heal_amount", "heal_pct", "base_heal", "hp_heal", "heal_per_second");

    /** Ally-target save skills, summed, that make a 1. */
    private double saveFull = 1.5;

    /**
     * Attribute keys of skills that make the hero strong in a fight without a farm: crit, steal.
     */
    private List<String> scalingKeys =
        List.of(
            "crit_chance",
            "crit_multiplier",
            "crit_bonus",
            "lifesteal",
            "lifesteal_percent",
            "lifesteal_pct",
            "max_illusions",
            "illusion_duration");

    /** A scaling skill counts this much towards farm priority. */
    private double scalingSkill = 0.3;

    /** A low-cooldown area damage skill clears creeps; at most this cooldown. */
    private double farmSkillMaxCooldown = 14;

    private double farmSkill = 0.1;

    /** Body: an agility hero gets this, a ranged one this more, a Carry tag this more. */
    private double agilityBody = 0.25;

    private double rangedBody = 0.1;
    private double carryTag = 0.25;

    /** The value a Jungler tag gives to the jungle function. */
    private double junglerTag = 1.0;

    /** The tag names of OpenDota's {@code roles} list. */
    private String carryTagName = "Carry";

    private String junglerTagName = "Jungler";
    private String durableTagName = "Durable";

    /** Attribute keys of skills that make the hero or an ally take less damage. */
    private List<String> durabilityKeys =
        List.of("bonus_armor", "damage_reduction", "barrier", "barrier_amount");

    /** Durability skills, summed, that fill {@code durabilitySkillShare} of the value. */
    private double durabilitySkillFull = 2.0;

    private double durabilitySkillShare = 0.4;

    /** Strength at this level and armor at the start that make the full body value. */
    private int bodyLevel = 15;

    private double strengthFull = 90;
    private double armorFull = 6;
    private double strengthShare = 0.4;
    private double armorShare = 0.1;
    private double durableTag = 0.1;

    /** Move speed above this, scaled to the share below, helps roaming. */
    private double moveSpeedBase = 300;

    private double moveSpeedFull = 30;
    private double moveSpeedShare = 0.2;
  }

  /** How the stored behaviour of a player becomes a profile. */
  @Data
  public static class Observed {
    /** Share of the laning phase in the jungle that makes a jungle of 1. */
    private double jungleFullShare = 0.3;

    /** OpenDota's lane role 4 (jungle) gives at least this jungle value. */
    private double jungleLaneRoleValue = 0.7;

    /** Share of the laning phase in lanes other than his main one that makes a roaming of 1. */
    private double roamFullShare = 0.5;

    /** From this share of the laning phase in the mid lane the lane is mid. */
    private double midLaneShare = 0.5;

    /**
     * The share of the team total of hero damage, hero healing, damage taken, wards placed and last
     * hits that makes the function 1; a player who does less gets the proportion. A team of equal
     * players has 0.2 each.
     */
    private double damageFullShare = 0.35;

    private double healFullShare = 0.5;
    private double wardFullShare = 0.5;
    private double lastHitFullShare = 0.35;
    private double damageTakenFullShare = 0.35;

    /**
     * The part of farm priority that comes from the share of last hits, the rest from net worth.
     */
    private double lastHitFarmShare = 0.3;
  }

  /** How a profile becomes a score per role and a best role. */
  @Data
  public static class Fit {
    /** What a function counts as when it is unknown, 0 to 1. */
    private double unknownValue = 0.5;

    /**
     * In the FINAL stage of a mode that fits profiles, the lane bonus is multiplied by this, so
     * that the net worth place and the farm carry more of the role than the lane.
     */
    private double finalLaneFactor = 0.1;

    /** What a function the FINAL stage does not know counts as: none, so it favours no role. */
    private double finalUnknownValue = 0.0;

    /** The drop of the team total when the player takes any other role that gives confidence 1. */
    private double fullConfidenceMargin = 2.0;

    /** Per role 1 to 5, the weight of each function in the score of that role. */
    private Map<Integer, Map<RoleFunction, Double>> weights = defaultWeights();

    /** Per role 1 to 5, the score the lane adds. */
    private Map<Integer, Map<LanePosition, Double>> laneBonus = defaultLaneBonus();

    private static Map<Integer, Map<RoleFunction, Double>> defaultWeights() {
      // order: farm, jungle, roaming, vision, initiation, disable, saveHeal, damage, durability
      double[][] table = {
        {3, 0, -1, -1, -0.5, -0.5, -1, 1, 0},
        {1.5, 0, 0.5, -0.5, 0, 0, -0.5, 2, -0.5},
        {0.5, 0.5, 0, 0, 2, 1, 0, 0, 2},
        {-1, 0.5, 2, 0.5, 1, 1.5, 0.5, 0.5, 0},
        {-3, -0.5, 0, 2, 0, 1, 2, -0.5, 0}
      };
      Map<Integer, Map<RoleFunction, Double>> weights = new java.util.HashMap<>();
      for (int role = 0; role < table.length; role++) {
        Map<RoleFunction, Double> row = new EnumMap<>(RoleFunction.class);
        for (RoleFunction function : RoleFunction.values()) {
          row.put(function, table[role][function.ordinal()]);
        }
        weights.put(role + 1, row);
      }
      return weights;
    }

    private static Map<Integer, Map<LanePosition, Double>> defaultLaneBonus() {
      // order: safe, mid, off; the table of the All Pick resolver, which the loop tunes later
      double[][] table = {
        {2, -0.5, -1},
        {-1.5, 3, -1.5},
        {-1, -0.5, 2},
        {0, 0.5, 1.5},
        {1.5, -0.5, 0}
      };
      Map<Integer, Map<LanePosition, Double>> bonus = new java.util.HashMap<>();
      for (int role = 0; role < table.length; role++) {
        Map<LanePosition, Double> row = new EnumMap<>(LanePosition.class);
        for (LanePosition lane : LanePosition.values()) {
          row.put(lane, table[role][lane.ordinal()]);
        }
        bonus.put(role + 1, row);
      }
      return bonus;
    }
  }
}
