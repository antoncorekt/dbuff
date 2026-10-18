package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deaths an item that spends health caused: the hero died within {@code surviveSeconds} of a use
 * and the enemy damage from the use to the death was less than the health he had before it. Each
 * costs a death ({@link DeathCost}).
 */
@Component
public class SelfHarmAspect implements ItemAspect {
  public static final String NAME = "self-harm";

  static final String SOUL_RING_DEATH = "SOUL_RING_DEATH";
  static final String SOUL_RING_HP_UNKNOWN = "SOUL_RING_HP_UNKNOWN";

  private static final Set<String> PARAMETERS = parameters();

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(WorthPrices.NAMES);
    names.addAll(Set.of("surviveSeconds", "hpLookbackSeconds", "maxHpWindowSeconds"));
    return Set.copyOf(names);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    HeroInputs hero = context.hero();
    SaveJudge judge = new SaveJudge(hero, context);
    DeathCost deathCost = new DeathCost(hero, WorthPrices.of(context));
    double window = context.price("surviveSeconds", 10.0);
    List<CombatEvent> uses = hero.uses(context.names());
    List<Detail> rows = new ArrayList<>();
    List<String> flags = new ArrayList<>();
    double gain = 0;
    boolean estimated = false;
    boolean unknown = false;
    for (CombatEvent use : uses) {
      double t = use.gameSecond();
      OptionalDouble death = judge.health().deathBetween(t, t + window);
      if (death.isEmpty()) {
        continue;
      }
      OptionalInt hpBefore = judge.judge(t).hpNow();
      if (hpBefore.isEmpty()) {
        unknown = true;
        continue;
      }
      double damage = judge.health().damage(t, death.getAsDouble());
      if (damage >= hpBefore.getAsInt()) {
        continue;
      }
      DeathCost.Cost cost = deathCost.at(death.getAsDouble());
      estimated |= cost.estimated();
      gain -= cost.value();
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("useSecond", t);
      figures.put("hpBefore", (double) hpBefore.getAsInt());
      figures.put("damageSince", damage);
      figures.put("selfHarmGain", -cost.value());
      rows.add(new Detail(SOUL_RING_DEATH, death.getAsDouble(), null, figures));
    }
    if (!rows.isEmpty()) {
      flags.add(SOUL_RING_DEATH);
    }
    if (unknown) {
      flags.add(SOUL_RING_HP_UNKNOWN);
    }
    if (estimated) {
      flags.add(SaveAspect.DEATH_COST_ESTIMATED);
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("selfDeaths", (double) rows.size());
    totals.put("selfHarmGain", gain);
    return new AspectPart(gain, true, totals, flags, rows, uses.size(), null, null);
  }
}
