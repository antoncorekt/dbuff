package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.item.StickUses.Use;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The sips of a bottle with what each restored. Health is what the combat log credits to the item
 * in the sip's window; when it credits the item with no heal at all in the match (the bottle's
 * regeneration is a modifier, not a heal), it is the constants' amount capped by the missing
 * health. Mana is always the constants' amount capped by the missing mana.
 */
record BottleSips(List<Use> uses, boolean healthEstimated, boolean manaEstimated) {
  static final Set<String> PARAMETERS =
      Stream.concat(
              SaveJudge.PARAMETERS.stream(),
              Stream.of("laningEndSeconds", "restoreFromConstants", "healWindowSeconds"))
          .collect(Collectors.toUnmodifiableSet());

  private static final String HEALTH_RESTORE = "health_restore";
  private static final String MANA_RESTORE = "mana_restore";

  static BottleSips of(AspectContext context) {
    HeroInputs hero = context.hero();
    SaveJudge judge = new SaveJudge(hero, context);
    List<CombatEvent> heals =
        hero.events().stream()
            .filter(
                e -> e.kind() == CombatEventKind.HEAL && context.names().contains(e.inflictor()))
            .toList();
    String key = HeroInputs.itemKey(context.names().getFirst());
    double hpAmount = context.kits().attribute(key, HEALTH_RESTORE).orElse(0);
    double manaAmount = context.kits().attribute(key, MANA_RESTORE).orElse(0);
    double window = context.price("healWindowSeconds", 4.0);
    boolean logged = !heals.isEmpty();
    List<Use> uses = new ArrayList<>();
    for (CombatEvent sip : hero.uses(context.names())) {
      double t = sip.gameSecond();
      SaveJudge.Verdict verdict = judge.judge(t);
      OptionalInt maxHp = verdict.maxHp();
      OptionalInt hpNow = verdict.hpNow();
      double hp =
          logged
              ? heals.stream()
                  .filter(h -> h.gameSecond() >= t && h.gameSecond() <= t + window)
                  .mapToDouble(CombatEvent::value)
                  .sum()
              : capped(hpAmount, maxHp, hpNow);
      HeroHealth.Mana manaNow = judge.health().mana(t);
      double mana = manaNow == null ? manaAmount : Math.min(manaAmount, manaNow.missing());
      uses.add(
          new Use(
              t,
              1,
              true,
              maxHp,
              hpNow,
              manaNow != null,
              hp,
              mana,
              t < context.price("laningEndSeconds", 600.0),
              verdict.saved(),
              verdict.survived(),
              verdict.damageAround()));
    }
    return new BottleSips(List.copyOf(uses), !logged, true);
  }

  private static double capped(double amount, OptionalInt maxHp, OptionalInt hpNow) {
    return maxHp.isPresent() && hpNow.isPresent()
        ? Math.min(amount, Math.max(0, maxHp.getAsInt() - hpNow.getAsInt()))
        : amount;
  }
}
