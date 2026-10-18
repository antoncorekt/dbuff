package com.ako.dbuff.service.replay.analysis.item;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Whether a use on a hero saved him: his health was low and heavy damage came around the use, and
 * he lived. The target may be the user or an ally.
 */
final class SaveJudge {
  static final Set<String> PARAMETERS =
      Set.of(
          "lowHpFraction",
          "lowHpAbsolute",
          "heavyDamageFraction",
          "heavyDamageAbsolute",
          "damageBeforeSeconds",
          "damageAfterSeconds",
          "surviveSeconds",
          "hpLookbackSeconds",
          "maxHpWindowSeconds");

  /**
   * @param maxHp his max health, from the track or estimated; empty when neither tells
   * @param hpNow his health at the use; empty when unknown
   * @param survived he did not die within {@code surviveSeconds}
   * @param saved low health under heavy damage, and he lived
   * @param damageAround the damage he took around the use
   */
  record Verdict(
      OptionalInt maxHp, OptionalInt hpNow, boolean survived, boolean saved, double damageAround) {}

  private final HeroHealth health;
  private final AspectContext context;

  SaveJudge(HeroInputs target, AspectContext context) {
    this.health = new HeroHealth(target.hero(), target.events(), target.track());
    this.context = context;
  }

  HeroHealth health() {
    return health;
  }

  Verdict judge(double second) {
    double window = context.price("maxHpWindowSeconds", 120.0);
    OptionalInt maxHp = health.maxHealth(second, window);
    OptionalInt hpNow = health.health(second, context.price("hpLookbackSeconds", 3.0), window);
    boolean survived = !health.diedBetween(second, second + context.price("surviveSeconds", 10.0));
    double damage =
        health.damage(
            second - context.price("damageBeforeSeconds", 3.0),
            second + context.price("damageAfterSeconds", 3.0));
    return new Verdict(
        maxHp, hpNow, survived, survived && lowAndHeavy(maxHp, hpNow, damage), damage);
  }

  private boolean lowAndHeavy(OptionalInt maxHp, OptionalInt hpNow, double damage) {
    if (hpNow.isEmpty()) {
      return false;
    }
    double lowHp =
        maxHp.isPresent()
            ? maxHp.getAsInt() * context.price("lowHpFraction", 0.35)
            : context.price("lowHpAbsolute", 300.0);
    double heavy =
        maxHp.isPresent()
            ? maxHp.getAsInt() * context.price("heavyDamageFraction", 0.3)
            : context.price("heavyDamageAbsolute", 300.0);
    return hpNow.getAsInt() <= lowHp && damage >= heavy;
  }
}
