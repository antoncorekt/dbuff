package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.item.StickUses.Use;
import java.util.List;

/**
 * The uses of an item that spends health for mana, as {@link Use}s {@link ChargeRestoreAspect}
 * sums: the health spent is a negative amount, the mana is the constants' amount capped by what was
 * missing.
 */
final class SoulRingUses {
  static final String HEALTH_COST = "healthCost";

  private static final String MANA_GAIN = "mana_gain";

  private SoulRingUses() {}

  static List<Use> of(AspectContext context) {
    HeroInputs hero = context.hero();
    SaveJudge judge = new SaveJudge(hero, context);
    double gain =
        context
            .kits()
            .attribute(HeroInputs.itemKey(context.names().getFirst()), MANA_GAIN)
            .orElse(0);
    double cost = context.price(HEALTH_COST, 0);
    return hero.uses(context.names()).stream()
        .map(use -> assess(context, judge, use, gain, cost))
        .toList();
  }

  private static Use assess(
      AspectContext context, SaveJudge judge, CombatEvent use, double gain, double cost) {
    double t = use.gameSecond();
    SaveJudge.Verdict verdict = judge.judge(t);
    HeroHealth.Mana manaNow = judge.health().mana(t);
    double mana = manaNow == null ? gain : Math.min(gain, manaNow.missing());
    return new Use(
        t,
        0,
        true,
        verdict.maxHp(),
        verdict.hpNow(),
        manaNow != null,
        -cost,
        mana,
        t < context.price("laningEndSeconds", 600.0),
        false,
        verdict.survived(),
        verdict.damageAround());
  }
}
