package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.item.VisionAspect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Wards, sentries, dust and the gem: what was bought and placed, the enemy wards removed while the
 * sentry was up, the invisible enemies killed where the item gave sight, and the observers
 * destroyed within a minute. There is a ratio only when the entry cost enough gold.
 */
@Component
public class VisionKindWording implements ItemKindWording {
  public static final String INVISIBLE_KILL = "INVISIBLE_KILL";

  @Override
  public String aspect() {
    return VisionAspect.NAME;
  }

  @Override
  public boolean complete(ItemEfficiency row) {
    return ItemWording.has(row.figures(), "visionBought");
  }

  @Override
  public String longVerdict(ItemEfficiency row) {
    return line(row) + ItemWording.limits(row.flags());
  }

  @Override
  public String shortVerdict(ItemEfficiency row, String subject) {
    return "%s: %s".formatted(subject, line(row));
  }

  /** {@code 8 sentries, 3 enemy wards removed, 1 Riki kill; worth 1.5x its cost}. */
  public static String line(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    List<String> parts = new ArrayList<>();
    long bought = Math.round(ItemWording.figure(f, "visionBought"));
    long placed = Math.round(ItemWording.figure(f, "visionPlaced"));
    switch (row.entryKey()) {
      case "ward_observer" ->
          parts.add(
              f.get("visionPlaced") == null
                  ? ItemWording.plural(bought, "observer") + " bought"
                  : ItemWording.plural(placed, "observer") + " placed");
      case "ward_sentry" ->
          parts.add(
              f.get("visionPlaced") == null
                  ? ItemWording.plural(bought, "sentry").replace("sentrys", "sentries") + " bought"
                  : placed == 1 ? "1 sentry" : placed + " sentries");
      case "dust" ->
          parts.add(
              "%d dust bought, %d used"
                  .formatted(bought, Math.round(ItemWording.figure(f, "visionDustUses"))));
      default -> parts.add("gem held");
    }
    if (f.get("visionObserverLifeSeconds") != null) {
      parts.add("lived " + ItemWording.clock(f.get("visionObserverLifeSeconds")) + " on average");
    }
    if (ItemWording.figure(f, "visionDewards") > 0) {
      parts.add(ItemWording.plural(Math.round(f.get("visionDewards")), "enemy ward") + " removed");
    }
    kills(row).ifPresent(parts::add);
    String text = String.join(", ", parts);
    long fast = Math.round(ItemWording.figure(f, "visionDewardedFast"));
    if (fast > 0) {
      text += "; %s dewarded within a minute".formatted(ItemWording.plural(fast, "observer"));
    }
    return text + ItemWording.worthClause(row.ratio());
  }

  private static java.util.Optional<String> kills(ItemEfficiency row) {
    Map<String, Integer> victims = new LinkedHashMap<>();
    for (Detail d : row.rows()) {
      if (d != null && INVISIBLE_KILL.equals(d.label()) && d.target() != null) {
        victims.merge(Prettify.unit(d.target()), 1, Integer::sum);
      }
    }
    if (victims.isEmpty()) {
      return java.util.Optional.empty();
    }
    int total = victims.values().stream().mapToInt(Integer::intValue).sum();
    if (victims.size() == 1) {
      return java.util.Optional.of(
          "%d %s %s"
              .formatted(total, victims.keySet().iterator().next(), total == 1 ? "kill" : "kills"));
    }
    return java.util.Optional.of(
        "%d invisible kills (%s)".formatted(total, String.join(", ", victims.keySet())));
  }
}
