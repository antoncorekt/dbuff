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
  static final String COST_UNKNOWN = "COST_UNKNOWN";

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
    double seconds() {
      return to - from;
    }

    double minutes() {
      return seconds() / SECONDS_PER_MINUTE;
    }
  }

  private ItemEfficiency evaluate(EntryContext context, HeroInputs hero, List<String> names) {
    ItemEfficiencyEntry entry = context.entry();
    String first = hero.held(names).getFirst();
    double bought = hero.firstAdded(List.of(first)).orElse(0);
    double end = context.gameEndSeconds();
    double length = entry.parameter("windowMinutes", 5) * SECONDS_PER_MINUTE;
    double gap = entry.parameter("campGapSeconds", 30);
    double ownedTo = Math.min(end, hero.ownedUntil(names, end));
    Long cost = context.cost(hero.held(names));
    Window before = window(hero, BEFORE, Math.max(0, bought - length), bought);
    Window after = window(hero, AFTER, bought, Math.min(ownedTo, bought + length));
    List<String> flags = new ArrayList<>();
    boolean comparable =
        before.seconds() > 0 && before.seconds() >= entry.parameter("minBeforeSeconds", 120.0);
    if (!comparable) {
      flags.add(NO_BEFORE_WINDOW);
    }
    if (after.seconds() <= 0 || after.seconds() < entry.parameter("minAfterSeconds", 60.0)) {
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
      gain = net * Math.max(0, ownedTo - bought) / SECONDS_PER_MINUTE;
      totals.put("netGoldPerMinute", net);
      if (cost != null) {
        ratio = gain / cost;
        double paidAt = bought + cost / net * SECONDS_PER_MINUTE;
        if (net > 0 && paidAt <= ownedTo) {
          payback = paidAt;
          rows.add(new Detail(PAYBACK, payback, null, Map.of("netGoldPerMinute", net)));
        }
      }
    }
    if (cost == null) {
      flags.add(COST_UNKNOWN);
    }
    totals.put("minutesOwned", Math.max(0, ownedTo - bought) / SECONDS_PER_MINUTE);
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
            .filter(k -> k.getSeconds() != null && !Boolean.TRUE.equals(k.getDenied()))
            .filter(k -> k.getSeconds() >= from && k.getSeconds() < to)
            .toList();
    return new Window(label, from, to, kills);
  }

  private static double goldPerMinute(Window w) {
    return gold(w) / w.minutes();
  }

  private static double gold(Window w) {
    return w.kills().stream().mapToInt(k -> k.getGold() == null ? 0 : k.getGold()).sum();
  }

  private Detail detail(Window w, double campGapSeconds) {
    Map<String, Double> figures = new LinkedHashMap<>();
    boolean measured = w.seconds() > 0;
    double minutes = w.minutes();
    figures.put("minutes", measured ? minutes : null);
    figures.put("kills", (double) w.kills().size());
    figures.put("killsPerMinute", measured ? w.kills().size() / minutes : null);
    figures.put("goldPerMinute", measured ? gold(w) / minutes : null);
    figures.put("campsPerMinute", measured ? camps(w, campGapSeconds) / minutes : null);
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
