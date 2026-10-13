package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import java.util.Map;
import java.util.function.Function;

/** Hand of Midas: gold and experience of the uses, payback, idle time. */
final class MidasWording implements Wording {
  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    if (row.count() == 0) {
      return "bought, never used" + Wording.limits(row.flags());
    }
    Map<String, Double> f = row.figures();
    double heroGold = f.getOrDefault("heroGold", 0.0);
    String share =
        heroGold > 0
            ? " (%d%% of his gold)".formatted(Math.round(100 * f.get("gold") / heroGold))
            : "";
    return ("%s, gave %s gold%s and %s XP, %s, ready but unused %s of the %s he farmed with"
            + " it, worth %s its cost%s")
        .formatted(
            Wording.plural(row.count(), "use"),
            LoopWording.gold(f.get("gold")),
            share,
            LoopWording.gold(f.get("xp")),
            row.paybackSeconds() == null
                ? "never paid for itself"
                : "paid for itself at " + LoopWording.clock(row.paybackSeconds()),
            LoopWording.clock(f.get("idleSeconds")),
            LoopWording.clock(f.get("farmingSeconds")),
            ItemLoopReport.ratio(row.ratio()),
            Wording.limits(row.flags()) + attackSpeed(f));
  }

  /** The attack speed it also gives is named, not counted. */
  private static String attackSpeed(Map<String, Double> f) {
    return f.containsKey("bonusAttackSpeed")
        ? " (it also gives +%s attack speed, not counted)"
            .formatted(LoopWording.gold(f.get("bonusAttackSpeed")))
        : "";
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    if ("PAYBACK".equals(detail.label())) {
      return new String[] {
        LoopWording.clock(detail.seconds()),
        "-",
        "paid for itself",
        "gain so far %s, it cost %s"
            .formatted(LoopWording.gold(f.get("cumulative")), LoopWording.gold(row.cost()))
      };
    }
    String extra =
        f.containsKey("extraGold")
            ? ", %s gold more than a normal kill".formatted(LoopWording.gold(f.get("extraGold")))
            : "";
    return new String[] {
      LoopWording.clock(detail.seconds()),
      Wording.creep(detail.target()),
      "%s gold, %s XP".formatted(LoopWording.gold(f.get("gold")), LoopWording.gold(f.get("xp"))),
      "%s, gain so far %s%s"
          .formatted(
              f.get("idleBefore") < 1
                  ? "used as soon as it was ready"
                  : "ready and unused for %s while he farmed"
                      .formatted(LoopWording.clock(f.get("idleBefore"))),
              LoopWording.gold(f.get("cumulative")),
              extra)
    };
  }
}
