package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Hand of Midas: gold and experience of the uses, payback, idle time. */
@Component
public class MidasKindWording implements ItemKindWording {
  @Override
  public String aspect() {
    return "midas-uses";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0
        || ItemWording.has(row.figures(), "gold", "xp", "idleSeconds", "farmingSeconds");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + ItemWording.limits(row.flags());
    }
    Map<String, Double> f = row.figures();
    double heroGold = ItemWording.figure(f, "heroGold");
    String share =
        heroGold > 0
            ? " (%d%% of his gold)".formatted(Math.round(100 * f.get("gold") / heroGold))
            : "";
    return ("%s, gave %s gold%s and %s XP, %s, ready but unused %s of the %s he farmed with"
            + " it, worth %s its cost%s")
        .formatted(
            ItemWording.plural(row.count(), "use"),
            BehaviourWording.gold(f.get("gold")),
            share,
            BehaviourWording.gold(f.get("xp")),
            row.paybackSeconds() == null
                ? "never paid for itself"
                : "paid for itself at " + ItemWording.clock(row.paybackSeconds()),
            ItemWording.clock(f.get("idleSeconds")),
            ItemWording.clock(f.get("farmingSeconds")),
            ItemWording.ratio(row.ratio()),
            ItemWording.limits(row.flags()) + attackSpeed(f));
  }

  /** The attack speed it also gives is named, not counted. */
  private static String attackSpeed(Map<String, Double> f) {
    return f.containsKey("bonusAttackSpeed")
        ? " (it also gives +%s attack speed, not counted)"
            .formatted(BehaviourWording.gold(f.get("bonusAttackSpeed")))
        : "";
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    if (row.count() == 0) {
      return subject + " was never used";
    }
    String payback =
        row.paybackSeconds() == null
            ? "has not paid for itself"
            : "paid for itself at " + ItemWording.clock(row.paybackSeconds());
    String worth = ItemWording.worth(row);
    return "%s %s and earned %s gold%s"
        .formatted(
            subject,
            payback,
            BehaviourWording.gold(row.figures().get("gold")),
            worth.isEmpty() ? "" : " (" + worth + ")");
  }
}
