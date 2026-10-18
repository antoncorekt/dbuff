package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The farming group: creeps, creep gold and camps per minute before and after the purchase. */
@Component
public class FarmingKindWording implements ItemKindWording {
  private static final String[] RATES = {"killsPerMinute", "goldPerMinute", "campsPerMinute"};
  private static final double NOTICEABLE_CAMPS = 0.5;
  private static final String FARM_GAIN = "gain.farm-rate";
  private static final String ITEM_DAMAGE_GAIN = "gain.item-damage";
  private static final String STAT_UPLIFT_GAIN = "gain.stat-uplift";

  @Override
  public String aspect() {
    return "farm-rate";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return window(row, "BEFORE").isPresent()
        && window(row, "AFTER").isPresent()
        && (row.ratio() == null || ItemWording.has(row.figures(), "netGoldPerMinute"));
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    Map<String, Double> before = window(row, "BEFORE").orElse(Map.of());
    Map<String, Double> after = window(row, "AFTER").orElse(Map.of());
    String rates =
        "creeps %s per min before and %s after, creep gold %s and %s per min, camps %s and %s per min"
            .formatted(
                ItemWording.number(ItemWording.figure(before, "killsPerMinute")),
                ItemWording.number(ItemWording.figure(after, "killsPerMinute")),
                BehaviourWording.gold(ItemWording.figure(before, "goldPerMinute")),
                BehaviourWording.gold(ItemWording.figure(after, "goldPerMinute")),
                ItemWording.number(ItemWording.figure(before, "campsPerMinute")),
                ItemWording.number(ItemWording.figure(after, "campsPerMinute")));
    if (row.ratio() == null) {
      return rates + ItemWording.limits(row.flags());
    }
    String payback =
        row.paybackSeconds() == null
            ? "not paid back by the end"
            : "paid back at " + ItemWording.clock(row.paybackSeconds());
    String net = BehaviourWording.gold(row.figures().get("netGoldPerMinute"));
    if (hasDamage(row)) {
      return "%s, net %s gold per min, %s, %s in total, %s%s"
          .formatted(
              rates,
              net,
              parts(row),
              ItemWording.ratio(row.ratio()),
              payback,
              ItemWording.limits(row.flags()));
    }
    return "%s, net %s gold per min, %s, worth %s its cost%s"
        .formatted(
            rates, net, payback, ItemWording.ratio(row.ratio()), ItemWording.limits(row.flags()));
  }

  private static boolean hasDamage(ItemEfficiency row) {
    return row.figures().get("damageValue") != null && row.cost() != null && row.cost() != 0;
  }

  /** {@code farm +0.5x, fights +0.4x (3 kills helped)}, each part in multiples of the cost. */
  private static String parts(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    double farm = f.get(FARM_GAIN) != null ? f.get(FARM_GAIN) : ItemWording.figure(f, "creepGain");
    double fights = fights(f);
    long kills = Math.round(ItemWording.figure(f, "killsHelped"));
    return "farm %s, fights %s%s"
        .formatted(
            ItemWording.signedRatio(farm / row.cost()),
            ItemWording.signedRatio(fights / row.cost()),
            kills > 0 ? " (%s helped)".formatted(ItemWording.plural(kills, "kill")) : "");
  }

  private static double fights(Map<String, Double> f) {
    if (f.get(ITEM_DAMAGE_GAIN) == null && f.get(STAT_UPLIFT_GAIN) == null) {
      return ItemWording.figure(f, "damageValue");
    }
    return ItemWording.figure(f, ITEM_DAMAGE_GAIN) + ItemWording.figure(f, STAT_UPLIFT_GAIN);
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    Map<String, Double> before = window(row, "BEFORE").orElse(Map.of());
    Map<String, Double> after = window(row, "AFTER").orElse(Map.of());
    double was = ItemWording.figure(before, "goldPerMinute");
    double now = ItemWording.figure(after, "goldPerMinute");
    String gold =
        "his creep gold %s per minute after buying it"
            .formatted(change(was, now, BehaviourWording::gold));
    String camps = camps(before, after);
    String damage = hasDamage(row) && row.ratio() != null ? damage(row) : "";
    if (row.ratio() == null) {
      return "%s: %s%s".formatted(subject, gold, camps);
    }
    return row.paybackSeconds() == null
        ? "%s has not paid back: %s%s%s".formatted(subject, gold, camps, damage)
        : "%s paid back at %s: %s%s%s"
            .formatted(subject, ItemWording.clock(row.paybackSeconds()), gold, camps, damage);
  }

  private static String damage(ItemEfficiency row) {
    double worth = fights(row.figures()) / row.cost();
    return ", its damage was worth %s its cost".formatted(ItemWording.ratio(worth));
  }

  private static String camps(Map<String, Double> before, Map<String, Double> after) {
    double was = ItemWording.figure(before, "campsPerMinute");
    double now = ItemWording.figure(after, "campsPerMinute");
    if (Math.abs(now - was) < NOTICEABLE_CAMPS) {
      return "";
    }
    return "; camps per minute %s".formatted(change(was, now, ItemWording::number));
  }

  private static String change(
      double was, double now, java.util.function.Function<Double, String> format) {
    if (format.apply(was).equals(format.apply(now))) {
      return "stayed at " + format.apply(now);
    }
    return "%s from %s to %s"
        .formatted(now > was ? "rose" : "fell", format.apply(was), format.apply(now));
  }

  private static Optional<Map<String, Double>> window(ItemEfficiency row, String label) {
    return row.rows().stream()
        .filter(d -> d != null && label.equals(d.label()))
        .map(Detail::figures)
        .filter(f -> ItemWording.has(f, RATES))
        .findFirst();
  }
}
