package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.health.SaveRule;
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

  private final SaveRule rule;

  record Verdict(
      OptionalInt maxHp, OptionalInt hpNow, boolean survived, boolean saved, double damageAround) {
    private static Verdict of(SaveRule.Verdict v) {
      return new Verdict(v.maxHp(), v.hpNow(), v.survived(), v.saved(), v.damageAround());
    }
  }

  SaveJudge(HeroInputs target, AspectContext context) {
    this.rule =
        new SaveRule(
            new HeroHealth(target.hero(), target.events(), target.track()),
            new SaveRule.Parameters(
                context.price("lowHpFraction", 0.35),
                context.price("lowHpAbsolute", 300.0),
                context.price("heavyDamageFraction", 0.3),
                context.price("heavyDamageAbsolute", 300.0),
                context.price("damageBeforeSeconds", 3.0),
                context.price("damageAfterSeconds", 3.0),
                context.price("surviveSeconds", 10.0),
                context.price("hpLookbackSeconds", 3.0),
                context.price("maxHpWindowSeconds", 120.0)));
  }

  HeroHealth health() {
    return rule.health();
  }

  Verdict judge(double second) {
    return Verdict.of(rule.judge(second));
  }
}
