package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Items cast on a hero: whom the casts saved and the kills they set up. */
@Component
public class TargetUseKindWording implements ItemKindWording {
  private static final String HERO_PREFIX = "npc_dota_hero_";
  private static final String HIMSELF = "himself";

  @Override
  public String aspect() {
    return "target-use";
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return row.count() == 0 || ItemWording.has(row.figures(), "saves");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + ItemWording.limits(row.flags());
    }
    return "%s, %s%s, worth %s its cost%s"
        .formatted(
            ItemWording.plural(row.count(), "cast"),
            saved(row),
            setUps(row),
            ItemWording.ratio(row.ratio()),
            ItemWording.limits(row.flags()));
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    if (row.count() == 0) {
      return subject + " was never used";
    }
    String worth = ItemWording.worth(row);
    return "%s: %s, %s%s%s"
        .formatted(
            subject,
            ItemWording.plural(row.count(), "cast"),
            saved(row),
            setUps(row),
            worth.isEmpty() ? "" : ", " + worth);
  }

  /** The hero's name as the game writes it, in words. */
  public static String hero(String name) {
    if (name == null) {
      return HIMSELF;
    }
    String bare = name.startsWith(HERO_PREFIX) ? name.substring(HERO_PREFIX.length()) : name;
    StringBuilder words = new StringBuilder();
    for (String part : bare.split("_")) {
      if (!part.isEmpty()) {
        words.append(words.isEmpty() ? "" : " ");
        words.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
      }
    }
    return words.toString();
  }

  private static String saved(ItemEfficiency row) {
    Map<String, Integer> byTarget = new LinkedHashMap<>();
    for (Detail d : row.rows()) {
      if (d.figures().getOrDefault("saved", 0.0) == 1.0) {
        byTarget.merge(d.target() == null ? HIMSELF : hero(d.target()), 1, Integer::sum);
      }
    }
    if (byTarget.isEmpty()) {
      return "no saves";
    }
    Integer own = byTarget.remove(HIMSELF);
    List<String> parts = new ArrayList<>();
    byTarget.forEach((name, times) -> parts.add(name + " " + times(times)));
    if (own != null) {
      parts.add(HIMSELF + " " + times(own));
    }
    return "saved "
        + (parts.size() == 1
            ? parts.getFirst()
            : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast());
  }

  private static String times(int count) {
    return count == 1 ? "once" : count == 2 ? "twice" : count + " times";
  }

  private static String setUps(ItemEfficiency row) {
    long kills = Math.round(ItemWording.figure(row.figures(), "setUps"));
    return kills == 0 ? "" : ", " + ItemWording.plural(kills, "kill") + " set up";
  }
}
