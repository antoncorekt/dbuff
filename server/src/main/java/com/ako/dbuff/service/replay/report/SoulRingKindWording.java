package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The Soul Ring: uses, mana gained, health spent, and the deaths it caused. */
@Component
public class SoulRingKindWording implements ItemKindWording {
  private static final String DEATH = "SOUL_RING_DEATH";

  @Override
  public String aspect() {
    return "self-harm";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0 || ItemWording.has(row.figures(), "hp", "mana", "selfDeaths");
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
    String times =
        row.rows().stream()
            .filter(r -> DEATH.equals(r.label()))
            .map(r -> ItemWording.clock(r.seconds()))
            .collect(Collectors.joining(", "));
    long deaths = Math.round(ItemWording.figure(f, "selfDeaths"));
    return "%s, %s mana, cost %s HP; %s"
        .formatted(
            ItemWording.plural(row.count(), "use"),
            BehaviourWording.gold(f.get("mana")),
            BehaviourWording.gold(Math.abs(ItemWording.figure(f, "hp"))),
            deaths == 0
                ? "no death it caused"
                : "%s it caused at %s".formatted(ItemWording.plural(deaths, "death"), times));
  }
}
