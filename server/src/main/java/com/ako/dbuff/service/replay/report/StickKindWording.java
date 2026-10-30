package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Magic Stick and Wand: health and mana restored, saves, deaths soon after a use. */
@Component
public class StickKindWording implements ItemKindWording {
  @Override
  public String aspect() {
    return "charge-restore";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0 || ItemWording.has(row.figures(), "hp", "mana");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + ItemWording.limits(row.flags());
    }
    Map<String, Double> f = row.figures();
    long saves = Math.round(ItemWording.figure(f, "saves"));
    long died = Math.round(ItemWording.figure(f, "diedSoon"));
    return "%s, restored %s HP and %s mana, %s%s%s%s"
        .formatted(
            ItemWording.plural(row.count(), "use"),
            BehaviourWording.gold(f.get("hp")),
            BehaviourWording.gold(f.get("mana")),
            saves == 0 ? "no saves" : saves == 1 ? "1 save" : saves + " saves",
            died == 0 ? "" : ", died within 10 s after %d of them".formatted(died),
            ItemWording.worthClause(row.ratio()),
            ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    if (row.count() == 0) {
      return subject + " was never used";
    }
    long saves = Math.round(ItemWording.figure(row.figures(), "saves"));
    String worth = ItemWording.worth(row);
    return "%s: %s, %s%s"
        .formatted(
            subject,
            ItemWording.plural(row.count(), "use"),
            saves == 0 ? "no saves" : ItemWording.plural(saves, "save"),
            worth.isEmpty() ? "" : ", " + worth);
  }
}
