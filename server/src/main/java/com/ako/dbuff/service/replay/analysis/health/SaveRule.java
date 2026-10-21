package com.ako.dbuff.service.replay.analysis.health;

import java.util.OptionalInt;

/**
 * Whether a hero was saved at a moment: his health was low and heavy damage came around it, and he
 * lived. Falls back to absolute amounts when his max health is unknown.
 */
public class SaveRule {
  /**
   * @param lowHpFraction low health, as a share of max health
   * @param lowHpAbsolute low health when max health is unknown
   * @param heavyDamageFraction heavy damage, as a share of max health
   * @param heavyDamageAbsolute heavy damage when max health is unknown
   * @param damageBeforeSeconds how far before the moment damage counts
   * @param damageAfterSeconds how far after the moment damage counts
   * @param surviveSeconds how long after the moment he must live
   * @param hpLookbackSeconds how old the last hit may be to tell his health
   * @param maxHpWindowSeconds how far around the moment a max health estimate counts
   */
  public record Parameters(
      double lowHpFraction,
      double lowHpAbsolute,
      double heavyDamageFraction,
      double heavyDamageAbsolute,
      double damageBeforeSeconds,
      double damageAfterSeconds,
      double surviveSeconds,
      double hpLookbackSeconds,
      double maxHpWindowSeconds) {
    public static Parameters defaults() {
      return new Parameters(0.35, 300.0, 0.3, 300.0, 3.0, 3.0, 10.0, 3.0, 120.0);
    }
  }

  /**
   * @param maxHp his max health, from the track or estimated; empty when neither tells
   * @param hpNow his health at the moment; empty when unknown
   * @param survived he did not die within {@code surviveSeconds}
   * @param saved low health under heavy damage, and he lived
   * @param damageAround the damage he took around the moment
   */
  public record Verdict(
      OptionalInt maxHp, OptionalInt hpNow, boolean survived, boolean saved, double damageAround) {}

  private final HeroHealth health;
  private final Parameters parameters;

  public SaveRule(HeroHealth health, Parameters parameters) {
    this.health = health;
    this.parameters = parameters;
  }

  public HeroHealth health() {
    return health;
  }

  public Verdict judge(double second) {
    OptionalInt maxHp = health.maxHealth(second, parameters.maxHpWindowSeconds());
    OptionalInt hpNow =
        health.health(second, parameters.hpLookbackSeconds(), parameters.maxHpWindowSeconds());
    boolean survived = !health.diedBetween(second, second + parameters.surviveSeconds());
    double damage =
        health.damage(
            second - parameters.damageBeforeSeconds(), second + parameters.damageAfterSeconds());
    return new Verdict(
        maxHp, hpNow, survived, survived && lowAndHeavy(maxHp, hpNow, damage), damage);
  }

  private boolean lowAndHeavy(OptionalInt maxHp, OptionalInt hpNow, double damage) {
    if (hpNow.isEmpty()) {
      return false;
    }
    double lowHp =
        maxHp.isPresent()
            ? maxHp.getAsInt() * parameters.lowHpFraction()
            : parameters.lowHpAbsolute();
    double heavy =
        maxHp.isPresent()
            ? maxHp.getAsInt() * parameters.heavyDamageFraction()
            : parameters.heavyDamageAbsolute();
    return hpNow.getAsInt() <= lowHp && damage >= heavy;
  }
}
