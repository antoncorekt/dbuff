package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.death.DeathReason;
import com.ako.dbuff.service.replay.analysis.fight.AbsenceReason;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.reasons.*}. Every number is a first-guess hypothesis of how
 * much a reason matters, to be tuned in the review rounds; a reason is stored whatever its
 * severity, and ranking or capping is left to the reader of the rows.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.reasons")
public class ReasonProperties {
  /** Used for a reason code that has no entry below. */
  private double defaultSeverity = 0.3;

  private Map<AbsenceReason, Double> absenceSeverity =
      new EnumMap<>(
          Map.of(
              AbsenceReason.NO_TP, 0.7,
              AbsenceReason.TP_ON_COOLDOWN, 0.5,
              AbsenceReason.HAD_TP_NOT_USED, 0.8));

  /** An ability or item that could not be cast because it was cooling down. */
  private double onCooldownSeverity = 0.4;

  private double noManaSeverity = 0.5;

  /** Added for each further ability of the same hero and reason in the fight. */
  private double repeatedUseStep = 0.1;

  /** Multiplies an ability reason of a hero who was near the fight but took no part in it. */
  private double passivePresenceFactor = 0.5;

  /** Reasons the death analysis gave; REINCARNATED and OTHER explain nothing and are left out. */
  private Map<DeathReason, Double> deathSeverity =
      new EnumMap<>(
          Map.ofEntries(
              Map.entry(DeathReason.DIED_IMMUNE, 0.1),
              Map.entry(DeathReason.BKB_PIERCED, 0.2),
              Map.entry(DeathReason.BKB_EXPIRED, 0.4),
              Map.entry(DeathReason.BKB_READY_UNUSED, 0.8),
              Map.entry(DeathReason.BKB_ON_COOLDOWN, 0.5),
              Map.entry(DeathReason.DISABLE_CHAIN, 0.3),
              Map.entry(DeathReason.CAUGHT_OUT, 0.7),
              Map.entry(DeathReason.TOWER, 0.4),
              Map.entry(DeathReason.BURST, 0.2),
              Map.entry(DeathReason.OUT_OF_POSITION, 0.7),
              Map.entry(DeathReason.NO_BKB, 0.3)));

  /** The first hero to die in a fight with at least {@link #diedFirstMinDeaths} deaths. */
  private double diedFirstSeverity = 0.5;

  private int diedFirstMinDeaths = 2;

  /** By final role: a carry or mid who was farming while his team fought without him. */
  private Map<Integer, Double> carryFarmingSeverity = Map.of(1, 0.8, 2, 0.6);

  /** By final role: a soft or hard support who was alive and not in the fight. */
  private Map<Integer, Double> supportAbsentSeverity = Map.of(4, 0.4, 5, 0.3);

  /** A function the losing team's final composition lacked; the others use the default. */
  private Map<CompositionToken, Double> roleGapSeverity =
      new EnumMap<>(
          Map.of(
              CompositionToken.NO_INITIATOR, 0.5,
              CompositionToken.NO_DISABLE, 0.5,
              CompositionToken.NO_FRONTLINE, 0.4,
              CompositionToken.NO_SAVE_HEAL, 0.3,
              CompositionToken.NO_WAVECLEAR, 0.2,
              CompositionToken.NO_VISION_DUTY, 0.2));

  /**
   * Ability Draft: a hero in the fight who played another role than his skills were drafted for.
   */
  private double intentNotPlayedBase = 0.3;

  /** Added for each step between the drafted role and the played one (1 to 4). */
  private double intentNotPlayedDistanceStep = 0.15;

  /** Ability Draft: the functions that the team's drafted skills, taken together, must reach. */
  private Set<RoleFunction> weakCombinationFunctions =
      EnumSet.of(RoleFunction.INITIATION, RoleFunction.DISABLE, RoleFunction.DAMAGE);

  /** A function is low when no player of the team reaches this value in his INTENT profile. */
  private double weakCombinationLow = 0.6;

  /** How many of those functions must be low for the combination to be weak. */
  private int weakCombinationMinGaps = 2;

  private WeakCombination weakCombination = new WeakCombination();

  private double weakCombinationBase = 0.4;

  /** Added for each lacking function beyond the minimum. */
  private double weakCombinationStep = 0.2;

  /**
   * Ability Draft: a skill nobody picked is strong when one of these functions reaches the value.
   */
  private Set<RoleFunction> strongSkillFunctions =
      EnumSet.of(RoleFunction.INITIATION, RoleFunction.DISABLE, RoleFunction.DAMAGE);

  private StrongSkillLeft strongSkillLeft = new StrongSkillLeft();

  private double strongSkillMinValue = 0.35;

  private double strongSkillBase = 0.3;

  /** Added for each further strong skill left in the pool. */
  private double strongSkillStep = 0.1;

  /** All Pick: the hero tags of the constants that fit each final role; a hero with none is out. */
  private Map<Integer, Set<String>> heroRoleTags =
      Map.of(
          1, Set.of("Carry"),
          2, Set.of("Carry", "Nuker"),
          3, Set.of("Durable", "Initiator"),
          4, Set.of("Support", "Initiator", "Disabler"),
          5, Set.of("Support"));

  /** All Pick: by final role, the severity of a hero whose tags do not fit it. */
  private Map<Integer, Double> heroRoleMismatchSeverity =
      Map.of(1, 0.6, 2, 0.4, 3, 0.3, 4, 0.3, 5, 0.3);

  /**
   * A power rune whose effect is active in the fight, by rune; it is a factor, not a cause, so the
   * values stay below the reasons of what happened in the fight.
   */
  private Map<RuneType, Double> runeAdvantageSeverity =
      new EnumMap<>(
          Map.of(
              RuneType.DOUBLE_DAMAGE, 0.5,
              RuneType.HASTE, 0.4,
              RuneType.ARCANE, 0.3,
              RuneType.INVISIBILITY, 0.4,
              RuneType.SHIELD, 0.4));

  /** The severity of a reason about a player, lowered when he was near the fight but passive. */
  public double byPresence(double severity, Presence presence) {
    return presence == Presence.PRESENT_PASSIVE ? severity * passivePresenceFactor : severity;
  }

  public double severity(AbsenceReason reason) {
    return absenceSeverity.getOrDefault(reason, defaultSeverity);
  }

  public double severity(DeathReason reason) {
    return deathSeverity.getOrDefault(reason, defaultSeverity);
  }

  public double severity(RuneType type) {
    return runeAdvantageSeverity.getOrDefault(type, defaultSeverity);
  }

  public double severity(CompositionToken token) {
    return roleGapSeverity.getOrDefault(token, defaultSeverity);
  }

  @Data
  public static class WeakCombination {
    /** The losing team needs at least this many players with an INTENT profile to be judged. */
    private int minProfiledPlayers = 4;
  }

  @Data
  public static class StrongSkillLeft {
    /**
     * Off by default: the single-skill scoring misses disables and initiation of well-known skills,
     * and the reason repeats the same draft-level finding in every lost fight.
     */
    private boolean enabled = false;
  }
}
