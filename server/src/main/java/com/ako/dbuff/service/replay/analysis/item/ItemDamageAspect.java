package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * The item's own damage to enemy heroes (chain lightning, static charge, burn, cleave): the damage
 * entries of the combat log whose inflictor is the item, from the purchase to the end of ownership,
 * valued by {@link KillShare}: a share of each kill it helped, a poke price for the rest.
 */
@Component
public class ItemDamageAspect implements ItemAspect {
  public static final String NAME = "item-damage";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return DamageAspects.PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    FarmWindows windows = FarmWindows.of(context);
    DamageComponent damage = DamageAspects.estimate(context, windows);
    KillShare share = new KillShare(context.hero(), WorthPrices.of(context));
    Predicate<CombatEvent> own = DamageAspects.during(windows, context.names());
    double value = share.value(own);
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("procDamage", damage.procDamage());
    figures.put("damageValue", value);
    figures.put("killsHelped", (double) share.killsHelped(own));
    return DamageAspects.part(
        NAME, windows, value, figures, List.of(), new LinkedHashMap<>(figures));
  }
}
