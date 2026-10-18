package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sums the parts the aspects of an entry measured for one hero into one {@link ItemEfficiency}. */
final class AspectSum {
  static final String COST_UNKNOWN = "COST_UNKNOWN";

  private static final String PAYBACK = "PAYBACK";
  private static final double SECONDS_PER_MINUTE = 60;

  private AspectSum() {}

  /** One row per hero who held one of the entry's items, judged by the entry's aspects. */
  static List<ItemEfficiency> judge(
      ItemEfficiencyEntry entry,
      List<ItemAspect> aspects,
      ItemKits kits,
      int gameEndSeconds,
      List<HeroInputs> heroes) {
    List<ItemEfficiency> rows = new ArrayList<>();
    for (HeroInputs hero : heroes) {
      AspectContext context = AspectContext.of(entry, kits, gameEndSeconds, hero);
      if (!context.held().isEmpty()) {
        rows.add(of(context, aspects.stream().map(a -> a.measure(context)).toList()));
      }
    }
    return rows;
  }

  /** The main aspect, the first listed, names the row's kind and counts its uses. */
  private static ItemEfficiency of(AspectContext context, List<AspectPart> parts) {
    ItemEfficiencyEntry entry = context.entry();
    Long cost = context.cost();
    double gain = parts.stream().mapToDouble(AspectPart::gain).sum();
    boolean measured = parts.stream().allMatch(AspectPart::measured);
    double bought = context.bought();
    Set<String> flags = new LinkedHashSet<>();
    parts.forEach(p -> flags.addAll(p.flags()));
    if (cost == null) {
      flags.add(COST_UNKNOWN);
    }
    List<Detail> rows = rows(parts);
    Double payback =
        parts.stream()
            .map(AspectPart::paybackSeconds)
            .filter(p -> p != null)
            .findFirst()
            .orElse(null);
    if (payback == null && cost != null && measured) {
      payback = paidBack(context, parts, cost, bought, rows);
    }
    return new ItemEfficiency(
        context.hero().slot(),
        entry.key(),
        HeroInputs.itemKey(context.held().getFirst()),
        entry.aspects().getFirst(),
        bought,
        parts.getFirst().count(),
        gain,
        cost,
        cost == null || !measured ? null : gain / cost,
        payback,
        figures(parts),
        List.copyOf(flags),
        rows);
  }

  private static Double paidBack(
      AspectContext context, List<AspectPart> parts, long cost, double bought, List<Detail> rows) {
    List<Double> rates =
        parts.stream().map(AspectPart::goldPerMinute).filter(r -> r != null).toList();
    if (rates.isEmpty()) {
      return null;
    }
    double rate = rates.stream().mapToDouble(Double::doubleValue).sum();
    double end = context.gameEndSeconds();
    double ownedTo = Math.min(end, context.hero().ownedUntil(context.names(), end));
    double paidAt = bought + cost / rate * SECONDS_PER_MINUTE;
    if (rate > 0 && paidAt <= ownedTo) {
      rows.add(new Detail(PAYBACK, paidAt, null, Map.of("netGoldPerMinute", rate)));
      return paidAt;
    }
    return null;
  }

  private static Map<String, Double> figures(List<AspectPart> parts) {
    Map<String, Double> totals = new LinkedHashMap<>();
    parts.forEach(p -> add(totals, p.figures()));
    return totals;
  }

  private static List<Detail> rows(List<AspectPart> parts) {
    Map<String, Detail> merged = new LinkedHashMap<>();
    for (AspectPart part : parts) {
      Map<String, Integer> seen = new LinkedHashMap<>();
      for (Detail row : part.rows()) {
        String base = row.label() + "|" + row.seconds() + "|" + row.target();
        String key = base + "|" + seen.merge(base, 1, Integer::sum);
        Detail present = merged.get(key);
        if (present == null) {
          merged.put(
              key,
              new Detail(
                  row.label(), row.seconds(), row.target(), new LinkedHashMap<>(row.figures())));
        } else {
          add(present.figures(), row.figures());
        }
      }
    }
    return new ArrayList<>(merged.values());
  }

  private static void add(Map<String, Double> into, Map<String, Double> figures) {
    figures.forEach(
        (name, value) -> {
          Double present = into.get(name);
          if (present != null && value != null) {
            into.put(name, present + value);
          } else {
            into.put(name, present != null ? present : value);
          }
        });
  }
}
