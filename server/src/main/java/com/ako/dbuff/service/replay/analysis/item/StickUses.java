package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The uses of a Magic Stick or Wand with what the hero's health, mana and charges were at each, the
 * facts {@link ChargeRestoreAspect} and {@link SaveAspect} judge. Whether a use saved the hero is
 * {@link SaveJudge}'s call.
 */
record StickUses(List<Use> uses) {
  static final Set<String> PARAMETERS =
      Stream.concat(
              SaveJudge.PARAMETERS.stream(), Stream.of("restorePerCharge", "laningEndSeconds"))
          .collect(Collectors.toUnmodifiableSet());

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
    SaveJudge judge = new SaveJudge(hero, context);
    return new StickUses(
        hero.uses(context.names()).stream().map(use -> assess(context, judge, use)).toList());
  }

  private static Use assess(AspectContext context, SaveJudge judge, CombatEvent use) {
    double t = use.gameSecond();
    OptionalInt seen = context.hero().chargesAt(context.names(), t);
    int charges = seen.orElse(0);
    double restored = charges * context.price("restorePerCharge", 15.0);
    SaveJudge.Verdict verdict = judge.judge(t);
    OptionalInt maxHp = verdict.maxHp();
    OptionalInt hpNow = verdict.hpNow();
    boolean hpKnown = maxHp.isPresent() && hpNow.isPresent();
    double hp =
        hpKnown ? Math.min(restored, Math.max(0, maxHp.getAsInt() - hpNow.getAsInt())) : restored;
    HeroHealth.Mana manaNow = judge.health().mana(t);
    double mana = manaNow == null ? restored : Math.min(restored, manaNow.missing());
    return new Use(
        t,
        charges,
        seen.isPresent(),
        maxHp,
        hpNow,
        manaNow != null,
        hp,
        mana,
        t < context.price("laningEndSeconds", 600.0),
        verdict.saved(),
        verdict.survived(),
        verdict.damageAround());
  }
}
