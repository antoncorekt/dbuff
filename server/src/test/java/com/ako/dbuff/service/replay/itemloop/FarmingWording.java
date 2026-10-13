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
    String payback =
        row.paybackSeconds() == null
            ? "not paid back by the end"
            : "paid back at " + LoopWording.clock(row.paybackSeconds());
    String net = LoopWording.gold(row.figures().get("netGoldPerMinute"));
    if (row.figures().containsKey("damageValue") && row.cost() != null) {
      return "%s, net %s gold per min, %s, %s in total, %s%s"
          .formatted(
              rates,
              net,
              parts(row),
              ItemLoopReport.ratio(row.ratio()),
              payback,
              Wording.limits(row.flags()));
    }
    return "%s, net %s gold per min, %s, worth %s its cost%s"
        .formatted(
            rates, net, payback, ItemLoopReport.ratio(row.ratio()), Wording.limits(row.flags()));
  }

  /** The gold part and the damage part of the worth, each against the cost. */
  private static String parts(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    if (!f.containsKey("damageValue") || row.cost() == null) {
      return "creeps only";
    }
    return "creeps %s its cost, damage %s"
        .formatted(
            signedRatio(f.getOrDefault("creepGain", 0.0) / row.cost()),
            signedRatio(f.get("damageValue") / row.cost()));
  }

  private static String signedRatio(double ratio) {
    return (ratio >= 0 ? "+" : "") + ItemLoopReport.ratio(ratio);
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
    if ("DAMAGE".equals(detail.label())) {
      return damageDetail(detail);
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

  private static String[] damageDetail(Detail detail) {
    Map<String, Double> f = detail.figures();
    String stats =
        f.containsKey("bonusDamage") || f.containsKey("bonusAttackSpeed")
            ? "; its stats: +%s damage, +%s attack speed"
                .formatted(
                    LoopWording.gold(f.getOrDefault("bonusDamage", 0.0)),
                    LoopWording.gold(f.getOrDefault("bonusAttackSpeed", 0.0)))
            : "";
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "enemy heroes",
      ("%s damage from the item's own effects and %s more from its stats, worth %s gold at %s per"
              + " 1k damage")
          .formatted(
              LoopWording.gold(f.get("procDamage")),
              LoopWording.gold(f.get("passiveDamage")),
              LoopWording.gold(f.get("damageValue")),
              LoopWording.gold(f.get("damagePrice"))),
      ("hero damage per min %s before the purchase and %s after (the stage of the game also raises"
              + " it)%s")
          .formatted(
              LoopWording.gold(f.get("beforePerMinute")),
              LoopWording.gold(f.get("afterPerMinute")),
              stats)
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
