package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The Bottle: sips, health and mana restored, runes taken while holding it and bottled. */
@Component
public class BottleKindWording implements ItemKindWording {
  @Override
  public String aspect() {
    return "bottle-runes";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0 || ItemWording.has(row.figures(), "hp", "mana", "runes", "bottled");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + ItemWording.limits(row.flags());
    }
    return "%s, worth %s its cost%s"
        .formatted(figures(row), ItemWording.ratio(row.ratio()), ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    if (row.count() == 0) {
      return subject + " was never used";
    }
    String worth = ItemWording.worth(row);
    return "%s: %s%s".formatted(subject, figures(row), worth.isEmpty() ? "" : ", " + worth);
  }

  private static String figures(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    return "%s, %s HP, %s mana, %s (%d bottled)"
        .formatted(
            ItemWording.plural(row.count(), "sip"),
            BehaviourWording.gold(f.get("hp")),
            BehaviourWording.gold(f.get("mana")),
            ItemWording.plural(Math.round(ItemWording.figure(f, "runes")), "rune"),
            Math.round(ItemWording.figure(f, "bottled")));
  }
}
