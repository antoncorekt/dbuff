package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * What the item's stats (attack damage and speed) added to the hero's damage to enemy heroes: his
 * damage per minute in the window after the purchase less the window before, less the item's own
 * damage per minute, less the drift of his damage trend before the purchase, kept at 0 or more and
 * carried over the whole ownership. It is valued at what his damage was worth by {@link KillShare}
 * while he owned the item, and split with the items bought within {@code statShareSeconds} of it by
 * their attack damage and speed.
 */
@Component
public class StatUpliftAspect implements ItemAspect {
  public static final String NAME = "stat-uplift";
  static final String NO_DAMAGE_COMPARISON = "NO_DAMAGE_COMPARISON";

  private static final String[] STATS = {"bonus_damage", "bonus_attack_speed"};
  private static final double DEFAULT_SHARE_SECONDS = 120;

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
    double share = statShare(context, windows.bought());
    double passive = damage.passiveDamage() * share;
    double value = passive * valuePerDamage(context, windows);
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("passiveDamage", passive);
    figures.put("damageValue", value);
    Map<String, Double> row = new LinkedHashMap<>(figures);
    row.put("beforePerMinute", damage.beforePerMinute());
    row.put("afterPerMinute", damage.afterPerMinute());
    row.put("driftPerMinute", damage.compared() ? damage.driftPerMinute() : null);
    row.put("statShare", share);
    String key = HeroInputs.itemKey(context.held().getFirst());
    context.kits().attribute(key, "bonus_damage").ifPresent(v -> row.put("bonusDamage", v));
    context
        .kits()
        .attribute(key, "bonus_attack_speed")
        .ifPresent(v -> row.put("bonusAttackSpeed", v));
    return DamageAspects.part(
        NAME,
        windows,
        value,
        figures,
        damage.compared() ? List.of() : List.of(NO_DAMAGE_COMPARISON),
        row);
  }

  private static double valuePerDamage(AspectContext context, FarmWindows windows) {
    Predicate<CombatEvent> all = DamageAspects.during(windows, null);
    WorthPrices prices = WorthPrices.of(context);
    double dealt = context.hero().heroDamage(windows.bought(), windows.ownedTo());
    return dealt > 0
        ? new KillShare(context.hero(), prices).value(all) / dealt
        : prices.pokePrice();
  }

  /**
   * This item's part of the stats bought around the same time: its attack damage plus attack speed
   * over the same sum of every other item bought within the window, 1 when nothing else was.
   */
  private static double statShare(AspectContext context, double bought) {
    double window = context.entry().parameter("statShareSeconds", DEFAULT_SHARE_SECONDS);
    Set<String> components = new LinkedHashSet<>();
    for (String name : context.held()) {
      context
          .kits()
          .item(HeroInputs.itemKey(name))
          .map(item -> item.getComponents())
          .ifPresent(components::addAll);
    }
    Set<String> others = new LinkedHashSet<>();
    context.hero().changes().stream()
        .filter(c -> StateChangeKind.ITEM_ADDED.name().equals(c.getKind()))
        .filter(c -> Math.abs(c.getGameSeconds() - bought) <= window)
        .map(c -> c.getName())
        .filter(n -> !context.names().contains(n))
        .map(HeroInputs::itemKey)
        .filter(k -> !components.contains(k))
        .forEach(others::add);
    double mine = context.held().stream().mapToDouble(n -> weight(context, n)).max().orElse(0);
    double rest = others.stream().mapToDouble(k -> weight(context, HeroInputs.replayName(k))).sum();
    return mine + rest == 0 ? 1 : mine / (mine + rest);
  }

  private static double weight(AspectContext context, String replayName) {
    String key = HeroInputs.itemKey(replayName);
    double weight = 0;
    for (String stat : STATS) {
      OptionalDouble value = context.kits().attribute(key, stat);
      weight += value.orElse(0);
    }
    return weight;
  }
}
