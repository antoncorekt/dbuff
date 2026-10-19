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
    return "%s, helped %s, %s%s"
        .formatted(
            ItemWording.plural(Math.round(ItemWording.figure(f, "extraCasts")), "extra cast"),
            ItemWording.plural(Math.round(ItemWording.figure(f, "killsHelped")), "kill"),
            ItemWording.ratio(row.ratio()),
            ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    Map<String, Double> f = row.figures();
    String worth = ItemWording.worth(row);
    return "%s gave %s, helped %s%s"
        .formatted(
            subject,
            ItemWording.plural(Math.round(ItemWording.figure(f, "extraCasts")), "extra cast"),
            ItemWording.plural(Math.round(ItemWording.figure(f, "killsHelped")), "kill"),
            worth.isEmpty() ? "" : " (" + worth + ")");
  }
}
