package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * A group of items bought to farm faster (Battle Fury, Maelstrom, Mjollnir...): creep kills, creep
 * gold and camps cleared per minute in the window before the hero's first item of the group and the
 * window after it, and what the difference pays against the item's cost. It is a before and after
 * comparison, not a proof: the game around the hero changes with time too.
 */
@Component
public class FarmingGroupEvaluator implements ItemEfficiencyEvaluator {
  static final String KIND = "farming-group";

  static final String NO_BEFORE_WINDOW = "NO_BEFORE_WINDOW";
  static final String SHORT_AFTER_WINDOW = "SHORT_AFTER_WINDOW";

  private static final String BEFORE = "BEFORE";
  private static final String AFTER = "AFTER";
  private static final String PAYBACK = "PAYBACK";
  private static final double SECONDS_PER_MINUTE = 60;
  private static final Set<String> PARAMETERS =
      Set.of("windowMinutes", "minBeforeSeconds", "minAfterSeconds", "campGapSeconds");

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

  private record Window(String label, double from, double to, List<CreepKillDomain> kills) {
    double minutes() {
      return (to - from) / SECONDS_PER_MINUTE;
    }
  }

  private ItemEfficiency evaluate(EntryContext context, HeroInputs hero, List<String> names) {
    ItemEfficiencyEntry entry = context.entry();
    String first = hero.held(names).getFirst();
    double bought = hero.firstAdded(List.of(first)).orElse(0);
    double end = context.gameEndSeconds();
    double length = entry.parameter("windowMinutes", 5) * SECONDS_PER_MINUTE;
    double gap = entry.parameter("campGapSeconds", 30);
    long cost = context.cost(List.of(first));
    Window before = window(hero, BEFORE, Math.max(0, bought - length), bought);
    Window after = window(hero, AFTER, bought, Math.min(end, bought + length));
    List<String> flags = new ArrayList<>();
    boolean comparable = before.to() - before.from() >= entry.parameter("minBeforeSeconds", 120);
    if (!comparable) {
      flags.add(NO_BEFORE_WINDOW);
    }
    if (after.to() - after.from() < entry.parameter("minAfterSeconds", 60)) {
      flags.add(SHORT_AFTER_WINDOW);
      comparable = false;
    }
    Map<String, Double> totals = new LinkedHashMap<>();
    List<Detail> rows = new ArrayList<>();
    rows.add(detail(before, gap));
    rows.add(detail(after, gap));
    double gain = 0;
    Double ratio = null;
    Double payback = null;
    if (comparable) {
      double net = goldPerMinute(after) - goldPerMinute(before);
      gain = net * (end - bought) / SECONDS_PER_MINUTE;
      ratio = gain / cost;
      totals.put("netGoldPerMinute", net);
      if (net > 0 && bought + cost / net * SECONDS_PER_MINUTE <= end) {
        payback = bought + cost / net * SECONDS_PER_MINUTE;
        rows.add(new Detail(PAYBACK, payback, null, Map.of("netGoldPerMinute", net)));
      }
    }
    totals.put("minutesAfterPurchase", Math.max(0, end - bought) / SECONDS_PER_MINUTE);
    return new ItemEfficiency(
        hero.slot(),
        entry.key(),
        HeroInputs.itemKey(first),
        KIND,
        bought,
        after.kills().size(),
        gain,
        cost,
        ratio,
        payback,
        totals,
        flags,
        rows);
  }

  private Window window(HeroInputs hero, String label, double from, double to) {
    List<CreepKillDomain> kills =
        hero.creepKills().stream()
            .filter(k -> !Boolean.TRUE.equals(k.getDenied()))
            .filter(k -> k.getSeconds() >= from && k.getSeconds() < to)
            .toList();
    return new Window(label, from, to, kills);
  }

  private static double goldPerMinute(Window w) {
    return w.minutes() <= 0 ? 0 : gold(w) / w.minutes();
  }

  private static double gold(Window w) {
    return w.kills().stream().mapToInt(k -> k.getGold() == null ? 0 : k.getGold()).sum();
  }

  private Detail detail(Window w, double campGapSeconds) {
    Map<String, Double> figures = new LinkedHashMap<>();
    double minutes = Math.max(w.minutes(), 1e-9);
    figures.put("minutes", w.minutes());
    figures.put("kills", (double) w.kills().size());
    figures.put("killsPerMinute", w.kills().size() / minutes);
    figures.put("goldPerMinute", gold(w) / minutes);
    figures.put("campsPerMinute", camps(w, campGapSeconds) / minutes);
    return new Detail(w.label(), w.from(), null, figures);
  }

  /** Camps cleared: neutral kills in one camp that follow each other within the gap are one. */
  private static int camps(Window w, double gapSeconds) {
    Map<Integer, Double> lastKill = new HashMap<>();
    int camps = 0;
    for (CreepKillDomain k : w.kills()) {
      CreepClass creep = CreepClass.of(k.getCreepName());
      if (creep == null || !creep.isNeutral() || k.getCamp() == null) {
        continue;
      }
      Double last = lastKill.put(k.getCamp(), k.getSeconds());
      if (last == null || k.getSeconds() - last > gapSeconds) {
        camps++;
      }
    }
    return camps;
  }
}
