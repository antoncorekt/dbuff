package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Cooldown reduction: the extra casts it allowed, the kills they helped and the worth. */
@Component
public class CooldownKindWording implements ItemKindWording {
  @Override
  public String aspect() {
    return "cooldown";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return ItemWording.has(row.figures(), "extraCasts", "killsHelped");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    return "%s%s%s%s"
        .formatted(
            casts(f),
            helped(f),
            ItemWording.worthClause(row.ratio()),
            ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    Map<String, Double> f = row.figures();
    String worth = ItemWording.worth(row);
    return "%s gave %s%s%s"
        .formatted(subject, casts(f), helped(f), worth.isEmpty() ? "" : " (" + worth + ")");
  }

  private static String casts(Map<String, Double> f) {
    long casts = Math.round(ItemWording.figure(f, "extraCasts"));
    return casts == 0 ? "no extra casts" : ItemWording.plural(casts, "extra cast");
  }

  private static String helped(Map<String, Double> f) {
    long kills = Math.round(ItemWording.figure(f, "killsHelped"));
    return kills == 0 ? "" : ", helped " + ItemWording.plural(kills, "kill");
  }
}
