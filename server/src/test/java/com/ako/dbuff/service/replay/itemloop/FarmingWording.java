package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import java.util.Map;
import java.util.function.Function;

/** The farming group: creeps, creep gold and camps per minute before and after the purchase. */
final class FarmingWording implements Wording {
  @Override
  public boolean showsUses() {
    return false;
  }

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    Map<String, Double> before = figures(row, "BEFORE");
    Map<String, Double> after = figures(row, "AFTER");
    String rates =
        "creeps %s per min before and %s after, creep gold %s and %s per min, camps %s and %s per min"
            .formatted(
                Wording.number(before.get("killsPerMinute")),
                Wording.number(after.get("killsPerMinute")),
                LoopWording.gold(before.get("goldPerMinute")),
                LoopWording.gold(after.get("goldPerMinute")),
                Wording.number(before.get("campsPerMinute")),
                Wording.number(after.get("campsPerMinute")));
    if (row.ratio() == null) {
      return rates + Wording.limits(row.flags());
    }
    return "%s, net %s gold per min, %s, worth %s its cost%s"
        .formatted(
            rates,
            LoopWording.gold(row.figures().get("netGoldPerMinute")),
            row.paybackSeconds() == null
                ? "not paid back by the end"
                : "paid back at " + LoopWording.clock(row.paybackSeconds()),
            ItemLoopReport.ratio(row.ratio()),
            Wording.limits(row.flags()));
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    if ("PAYBACK".equals(detail.label())) {
      return new String[] {
        LoopWording.clock(detail.seconds()),
        "-",
        "paid back",
        "net %s gold per min since the purchase against a cost of %s"
            .formatted(LoopWording.gold(f.get("netGoldPerMinute")), LoopWording.gold(row.cost()))
      };
    }
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "-",
      "%d creeps (%s per min), creep gold %s per min, camps %s per min"
          .formatted(
              Math.round(f.get("kills")),
              Wording.number(f.get("killsPerMinute")),
              LoopWording.gold(f.get("goldPerMinute")),
              Wording.number(f.get("campsPerMinute"))),
      "%s minutes %s the first purchase"
          .formatted(
              Wording.number(f.get("minutes")),
              "BEFORE".equals(detail.label()) ? "before" : "after")
    };
  }

  private static Map<String, Double> figures(ItemEfficiency row, String label) {
    return row.rows().stream()
        .filter(d -> label.equals(d.label()))
        .findFirst()
        .map(Detail::figures)
        .orElse(Map.of("killsPerMinute", 0.0, "goldPerMinute", 0.0, "campsPerMinute", 0.0));
  }
}
