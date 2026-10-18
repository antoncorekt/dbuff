package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The uses of a Magic Stick or Wand with what the hero's health, mana and charges were at each, the
 * facts {@link ChargeRestoreAspect} and {@link SaveAspect} judge.
 */
record StickUses(List<Use> uses) {
  static final Set<String> PARAMETERS =
      Set.of(
          "restorePerCharge",
          "lowHpFraction",
          "lowHpAbsolute",
          "heavyDamageFraction",
          "heavyDamageAbsolute",
          "damageBeforeSeconds",
          "damageAfterSeconds",
          "surviveSeconds",
          "hpLookbackSeconds",
          "maxHpWindowSeconds",
          "laningEndSeconds");

  /**
   * One use.
   *
   * @param hp the health restored, capped by what was missing when that is known
   * @param mana the mana restored, capped by what was missing when that is known
   * @param saved at low health under heavy damage, and the hero lived
   */
  record Use(
      double second,
      int charges,
      boolean chargesSeen,
      OptionalInt maxHp,
      OptionalInt hpNow,
      boolean manaKnown,
      double hp,
      double mana,
      boolean laning,
      boolean saved,
      boolean survived,
      double damageAround) {
    boolean hpKnown() {
      return maxHp.isPresent() && hpNow.isPresent();
    }
  }

  static StickUses of(AspectContext context) {
    HeroInputs hero = context.hero();
    HeroHealth health = new HeroHealth(hero.hero(), hero.events(), hero.track());
    return new StickUses(
        hero.uses(context.names()).stream()
            .map(use -> assess(context.entry(), hero, health, context.names(), use))
            .toList());
  }

  private static Use assess(
      ItemEfficiencyEntry entry,
      HeroInputs hero,
      HeroHealth health,
      List<String> names,
      CombatEvent use) {
    double t = use.gameSecond();
    OptionalInt seen = hero.chargesAt(names, t);
    int charges = seen.orElse(0);
    double restored = charges * entry.parameter("restorePerCharge", 15.0);
    double window = entry.parameter("maxHpWindowSeconds", 120.0);
    OptionalInt maxHp = health.maxHealth(t, window);
    OptionalInt hpNow = health.health(t, entry.parameter("hpLookbackSeconds", 3.0), window);
    boolean hpKnown = maxHp.isPresent() && hpNow.isPresent();
    double hp =
        hpKnown ? Math.min(restored, Math.max(0, maxHp.getAsInt() - hpNow.getAsInt())) : restored;
    HeroHealth.Mana manaNow = health.mana(t);
    double mana = manaNow == null ? restored : Math.min(restored, manaNow.missing());
    boolean survived = !health.diedBetween(t, t + entry.parameter("surviveSeconds", 10.0));
    boolean saved = survived && isSave(entry, health, t, maxHp, hpNow);
    double damageAround =
        health.damage(
            t - entry.parameter("damageBeforeSeconds", 3.0),
            t + entry.parameter("damageAfterSeconds", 3.0));
    return new Use(
        t,
        charges,
        seen.isPresent(),
        maxHp,
        hpNow,
        manaNow != null,
        hp,
        mana,
        t < entry.parameter("laningEndSeconds", 600.0),
        saved,
        survived,
        damageAround);
  }

  /** Low health at the use and heavy damage around it; the caller checks that he lived. */
  private static boolean isSave(
      ItemEfficiencyEntry entry,
      HeroHealth health,
      double second,
      OptionalInt maxHp,
      OptionalInt hpNow) {
    if (hpNow.isEmpty()) {
      return false;
    }
    double lowHp =
        maxHp.isPresent()
            ? maxHp.getAsInt() * entry.parameter("lowHpFraction", 0.35)
            : entry.parameter("lowHpAbsolute", 300.0);
    double heavy =
        maxHp.isPresent()
            ? maxHp.getAsInt() * entry.parameter("heavyDamageFraction", 0.3)
            : entry.parameter("heavyDamageAbsolute", 300.0);
    double damage =
        health.damage(
            second - entry.parameter("damageBeforeSeconds", 3.0),
            second + entry.parameter("damageAfterSeconds", 3.0));
    return hpNow.getAsInt() <= lowHp && damage >= heavy;
  }
}
