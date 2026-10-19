package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Arcane Boots, Mekansm, Guardian Greaves, Holy Locket: health and mana to allies, saves. */
@Component
public class TeamRestoreKindWording implements ItemKindWording {
  @Override
  public String aspect() {
    return "team-restore";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0 || ItemWording.has(row.figures(), "hp", "mana", "allies");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + ItemWording.limits(row.flags());
    }
    return "%s, worth %s its cost%s"
        .formatted(summary(row), ItemWording.ratio(row.ratio()), ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    if (row.count() == 0) {
      return subject + " was never used";
    }
    String worth = ItemWording.worth(row);
    return "%s: %s%s".formatted(subject, summary(row), worth.isEmpty() ? "" : ", " + worth);
  }

  /** {@code 9 uses, 2.1k HP and 1.4k mana to 3 allies, 1 save}. */
  public static String summary(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    long saves = Math.round(ItemWording.figure(f, "saves"));
    return "%s, %s HP and %s mana to %s, %s"
        .formatted(
            ItemWording.plural(row.count(), "use"),
            BehaviourWording.gold(f.get("hp")),
            BehaviourWording.gold(f.get("mana")),
            allies(Math.round(ItemWording.figure(f, "allies"))),
            saves == 0 ? "no saves" : ItemWording.plural(saves, "save"));
  }

  public static String allies(long count) {
    return count + (count == 1 ? " ally" : " allies");
  }
}
