package com.ako.dbuff.service.replay.analysis.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What the item's stats (attack damage and speed) added to the hero's damage to enemy heroes: his
 * damage per minute in the window after the purchase less the window before, less the item's own
 * damage per minute, kept at 0 or more and carried over the whole ownership. The stage of the game
 * confounds this: a hero deals more damage later whatever he holds.
 */
@Component
public class StatUpliftAspect implements ItemAspect {
  public static final String NAME = "stat-uplift";
  static final String NO_DAMAGE_COMPARISON = "NO_DAMAGE_COMPARISON";

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
    double value = damage.passiveValue(DamageAspects.price(context));
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("passiveDamage", damage.passiveDamage());
    figures.put("damageValue", value);
    Map<String, Double> row = new LinkedHashMap<>(figures);
    row.put("beforePerMinute", damage.beforePerMinute());
    row.put("afterPerMinute", damage.afterPerMinute());
    String key = HeroInputs.itemKey(context.held().getFirst());
    context.kits().attribute(key, "bonus_damage").ifPresent(v -> row.put("bonusDamage", v));
    context
        .kits()
        .attribute(key, "bonus_attack_speed")
        .ifPresent(v -> row.put("bonusAttackSpeed", v));
    return DamageAspects.part(
        windows,
        value,
        figures,
        damage.compared() ? List.of() : List.of(NO_DAMAGE_COMPARISON),
        row);
  }
}
