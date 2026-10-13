package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand of Midas, an investment: the gold and experience its uses gave, from the combat log (the
 * gold entry and the first experience entry right after the use), when that passed the cost, and
 * how long it was ready but unused while the hero farmed.
 */
@Component
@RequiredArgsConstructor
public class HandOfMidasEvaluator implements ItemEfficiencyEvaluator {
  static final String KIND = "hand-of-midas";

  static final String COOLDOWN_ESTIMATED = "COOLDOWN_ESTIMATED";
  static final String USE_WITHOUT_GOLD = "USE_WITHOUT_GOLD";
  static final String EXTRA_ESTIMATED = "EXTRA_ESTIMATED";

  private static final String USE = "USE";
  private static final String PAYBACK = "PAYBACK";
  private static final String BONUS_GOLD = "bonus_gold";
  private static final double PAIR_SECONDS = 0.3;
  private static final double COOLDOWN_LOOKAHEAD_SECONDS = 6;
  private static final Set<String> PARAMETERS = Set.of("cooldownSeconds", "xpWeight");

  private final CreepValues creepValues;

  @Override
  public boolean supports(ItemEfficiencyEntry entry) {
    return KIND.equals(entry.kind());
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public List<ItemEfficiency> evaluate(EntryContext context) {
    List<String> names = context.replayNames();
    return context.heroes().stream()
        .filter(hero -> !hero.held(names).isEmpty())
        .map(hero -> evaluate(context, hero, names))
        .toList();
  }

  private record Use(CombatEvent event, int gold, int xp) {}

  private ItemEfficiency evaluate(EntryContext context, HeroInputs hero, List<String> names) {
    ItemEfficiencyEntry entry = context.entry();
    double weight = entry.parameter("xpWeight", 1.0);
    double cooldown = entry.parameter("cooldownSeconds", 90);
    long cost = context.cost(hero.held(names));
    double bought = hero.firstAdded(names).orElse(0);
    double ownedTo = hero.ownedUntil(names, context.gameEndSeconds());
    List<Use> uses = pairedUses(context, hero, names);
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
      if (payback == null && cumulative >= cost) {
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
    double gain = gold + weight * xp;
    return new ItemEfficiency(
        hero.slot(),
        entry.key(),
        HeroInputs.itemKey(hero.held(names).getFirst()),
        KIND,
        bought,
        uses.size(),
        gain,
        cost,
        gain / cost,
        payback,
        totals,
        flags.stream().sorted().toList(),
        rows);
  }

  /** Each use with the gold entry of the bonus and the first experience entry right after it. */
  private List<Use> pairedUses(EntryContext context, HeroInputs hero, List<String> names) {
    int expected = bonusGold(context.kits(), context.entry().items().getFirst());
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
      int gold = take(events, taken, i, CombatEventKind.GOLD, expected);
      int xp = take(events, taken, i, CombatEventKind.XP, 0);
      uses.add(new Use(use, gold < 0 ? 0 : gold, xp < 0 ? 0 : xp));
    }
    return uses;
  }

  /** The value of the first unclaimed entry of the kind after the use within the pairing window. */
  private static int take(
      List<CombatEvent> events, Set<Integer> taken, int use, CombatEventKind kind, int wanted) {
    double t = events.get(use).gameSecond();
    for (int j = use + 1;
        j < events.size() && events.get(j).gameSecond() - t <= PAIR_SECONDS;
        j++) {
      CombatEvent e = events.get(j);
      if (e.kind() == kind && !taken.contains(j) && (wanted == 0 || e.value() == wanted)) {
        taken.add(j);
        return e.value();
      }
    }
    return -1;
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
