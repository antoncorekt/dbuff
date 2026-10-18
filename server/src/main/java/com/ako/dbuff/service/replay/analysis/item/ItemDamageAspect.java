package com.ako.dbuff.service.replay.analysis.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The item's own damage to enemy heroes (chain lightning, static charge, cleave): the damage
 * entries of the combat log whose inflictor is the item, from the purchase to the end of ownership,
 * at a price per 1000 damage.
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
    double price = DamageAspects.price(context);
    double value = damage.procValue(price);
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("procDamage", damage.procDamage());
    figures.put("damageValue", value);
    Map<String, Double> row = new LinkedHashMap<>(figures);
    row.put("damagePrice", price);
    return DamageAspects.part(windows, value, figures, List.of(), row);
  }
}
