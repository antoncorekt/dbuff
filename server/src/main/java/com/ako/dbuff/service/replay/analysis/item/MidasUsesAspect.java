package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.XpReason;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand of Midas, an investment: the gold and experience its uses gave, from the combat log (the
 * gold entry and the first experience entry right after the use), when that passed the cost, and
 * how long it was ready but unused while the hero farmed.
 */
@Component
@RequiredArgsConstructor
public class MidasUsesAspect implements ItemAspect {
  public static final String NAME = "midas-uses";

  static final String COOLDOWN_ESTIMATED = "COOLDOWN_ESTIMATED";
  static final String USE_WITHOUT_GOLD = "USE_WITHOUT_GOLD";
  static final String EXTRA_ESTIMATED = "EXTRA_ESTIMATED";
  static final String BONUS_GOLD_UNKNOWN = "BONUS_GOLD_UNKNOWN";

  private static final String USE = "USE";
  private static final String PAYBACK = "PAYBACK";
  private static final String BONUS_GOLD = "bonus_gold";
  private static final double PAIR_SECONDS = 0.3;
  private static final double COOLDOWN_LOOKAHEAD_SECONDS = 6;
  private static final Set<String> PARAMETERS =
      Set.of("cooldownSeconds", "xpWeight", "includeAttackSpeed");

  private final CreepValues creepValues;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  private record Use(CombatEvent event, int gold, int xp) {}

  @Override
  public AspectPart measure(AspectContext context) {
    ItemEfficiencyEntry entry = context.entry();
    HeroInputs hero = context.hero();
    List<String> names = context.names();
    double weight = entry.parameter("xpWeight", 1.0);
    double cooldown = entry.parameter("cooldownSeconds", 90);
    Long cost = context.cost();
    double bought = hero.firstAdded(names).orElse(0);
    double ownedTo = hero.ownedUntil(names, context.gameEndSeconds());
    int bonus = bonusGold(context.kits(), entry.items().getFirst());
    List<Use> uses = pairedUses(hero, names, bonus);
    List<Detail> rows = new ArrayList<>();
    Set<String> flags = new HashSet<>();
    double gold = 0;
    double xp = 0;
    double extra = 0;
    double idle = 0;
    double cumulative = 0;
    Double payback = null;
    double ready = bought;
    for (Use use : uses) {
      double t = use.event().gameSecond();
      double idleBefore = Math.max(0, hero.farmingSeconds(ready, t));
      idle += idleBefore;
      gold += use.gold();
      xp += use.xp();
      cumulative += use.gold() + weight * use.xp();
      Map<String, Double> figures = new LinkedHashMap<>();
      figures.put("gold", (double) use.gold());
      figures.put("xp", (double) use.xp());
      figures.put("idleBefore", idleBefore);
      figures.put("cumulative", cumulative);
      OptionalDouble normal = normalGold(use.event().target());
      if (normal.isPresent()) {
        figures.put("extraGold", use.gold() - normal.getAsDouble());
        extra += use.gold() - normal.getAsDouble();
        flags.add(EXTRA_ESTIMATED);
      }
      if (use.gold() == 0) {
        flags.add(USE_WITHOUT_GOLD);
      }
      rows.add(new Detail(USE, t, use.event().target(), figures));
      if (payback == null && cost != null && cumulative >= cost) {
        payback = t;
        rows.add(new Detail(PAYBACK, t, null, Map.of("cumulative", cumulative)));
      }
      OptionalDouble shown = hero.readyAfterUse(names.getFirst(), t, COOLDOWN_LOOKAHEAD_SECONDS);
      if (shown.isEmpty()) {
        flags.add(COOLDOWN_ESTIMATED);
      }
      ready = shown.orElse(t + cooldown);
    }
    idle += Math.max(0, hero.farmingSeconds(ready, ownedTo));
    Map<String, Double> totals = new LinkedHashMap<>();
    totals.put("gold", gold);
    totals.put("xp", xp);
    totals.put("extraGold", extra);
    totals.put("idleSeconds", idle);
    totals.put("farmingSeconds", hero.farmingSeconds(bought, ownedTo));
    totals.put("heroGold", total(hero, CombatEventKind.GOLD));
    totals.put("heroXp", total(hero, CombatEventKind.XP));
    if (entry.parameter("includeAttackSpeed", 0.0) > 0) {
      context
          .kits()
          .attribute(entry.items().getFirst(), "bonus_attack_speed")
          .ifPresent(v -> totals.put("bonusAttackSpeed", v));
    }
    double gain = gold + weight * xp;
    return new AspectPart(
        gain,
        true,
        totals,
        sortedFlags(flags, cost == null, bonus <= 0),
        rows,
        uses.size(),
        payback,
        null);
  }

  private static List<String> sortedFlags(Set<String> flags, boolean noCost, boolean noBonus) {
    if (noCost) {
      flags.add(AspectSum.COST_UNKNOWN);
    }
    if (noBonus) {
      flags.add(BONUS_GOLD_UNKNOWN);
    }
    return flags.stream().sorted().toList();
  }

  /**
   * Each use with the gold entry of the bonus (positive, equal to the constants' bonus; none is
   * claimed when the constants give no bonus) and the experience entry for a creep kill nearest in
   * time, each entry claimed once.
   */
  private List<Use> pairedUses(HeroInputs hero, List<String> names, int bonus) {
    List<CombatEvent> events = hero.events();
    Set<Integer> taken = new HashSet<>();
    List<Use> uses = new ArrayList<>();
    for (int i = 0; i < events.size(); i++) {
      CombatEvent use = events.get(i);
      if (use.kind() != CombatEventKind.ITEM
          || !hero.hero().equals(use.attacker())
          || !names.contains(use.inflictor())) {
        continue;
      }
      int gold =
          bonus <= 0
              ? 0
              : nearest(
                  events, taken, i, e -> e.kind() == CombatEventKind.GOLD && e.value() == bonus);
      int xp =
          nearest(
              events,
              taken,
              i,
              e ->
                  e.kind() == CombatEventKind.XP
                      && e.value() > 0
                      && e.xpReason() == XpReason.CREEP_KILL);
      uses.add(new Use(use, gold, xp));
    }
    return uses;
  }

  /** The value of the unclaimed matching entry nearest in time to the use within the window; 0. */
  private static int nearest(
      List<CombatEvent> events, Set<Integer> taken, int use, Predicate<CombatEvent> wanted) {
    double t = events.get(use).gameSecond();
    int best = -1;
    double bestGap = PAIR_SECONDS + 1e-9;
    for (int j = use + 1;
        j < events.size() && events.get(j).gameSecond() - t <= PAIR_SECONDS;
        j++) {
      best = closer(events, taken, wanted, j, t, best, bestGap);
      bestGap = best == j ? Math.abs(events.get(j).gameSecond() - t) : bestGap;
    }
    for (int j = use - 1; j >= 0 && t - events.get(j).gameSecond() <= PAIR_SECONDS; j--) {
      best = closer(events, taken, wanted, j, t, best, bestGap);
      bestGap = best == j ? Math.abs(events.get(j).gameSecond() - t) : bestGap;
    }
    if (best < 0) {
      return 0;
    }
    taken.add(best);
    return events.get(best).value();
  }

  private static int closer(
      List<CombatEvent> events,
      Set<Integer> taken,
      Predicate<CombatEvent> wanted,
      int j,
      double t,
      int best,
      double bestGap) {
    CombatEvent e = events.get(j);
    boolean nearer = Math.abs(e.gameSecond() - t) < bestGap;
    return !taken.contains(j) && wanted.test(e) && nearer ? j : best;
  }

  private static int bonusGold(ItemKits kits, String key) {
    return kits.item(key).map(ItemConstant::getAttrib).stream()
        .flatMap(List::stream)
        .filter(a -> BONUS_GOLD.equals(a.get("key")))
        .map(a -> a.get("value"))
        .filter(v -> v != null && v.matches("\\d+(\\.\\d+)?"))
        .mapToInt(v -> (int) Double.parseDouble(v))
        .findFirst()
        .orElse(0);
  }

  /** What a normal kill of the creep pays, from the measured values; empty for an unknown one. */
  private OptionalDouble normalGold(String creep) {
    CreepClass creepClass = CreepClass.of(creep);
    if (creepClass == null) {
      return OptionalDouble.empty();
    }
    return creepValues
        .ofUnit(creep)
        .or(() -> creepValues.of(creepClass))
        .map(v -> OptionalDouble.of(v.gold()))
        .orElse(OptionalDouble.empty());
  }

  private static double total(HeroInputs hero, CombatEventKind kind) {
    return hero.events().stream()
        .filter(e -> e.kind() == kind && e.value() > 0)
        .mapToDouble(CombatEvent::value)
        .sum();
  }
}
