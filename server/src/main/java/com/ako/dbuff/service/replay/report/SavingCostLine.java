package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.Map;
import java.util.Optional;

/**
 * What saving up for the item cost: {@code saved 3.8k for 6:10, died twice holding 2.1k (lost
 * 640)}.
 */
public final class SavingCostLine {
  public static final String LABEL = "SAVING";

  private SavingCostLine() {}

  /** The line, empty when the row has no saving window. */
  public static Optional<String> of(ItemEfficiency row) {
    return row.rows().stream()
        .filter(d -> d != null && LABEL.equals(d.label()))
        .map(Detail::figures)
        .filter(f -> ItemWording.has(f, "savingSeconds", "savingPeakGold", "savingDeaths"))
        .findFirst()
        .map(SavingCostLine::line);
  }

  private static String line(Map<String, Double> f) {
    String saved =
        "saved %s for %s"
            .formatted(
                BehaviourWording.gold(f.get("savingPeakGold")),
                ItemWording.clock(f.get("savingSeconds")));
    long deaths = Math.round(f.get("savingDeaths"));
    if (deaths == 0) {
      return saved + " without dying";
    }
    String died =
        "%s, died %s holding %s"
            .formatted(
                saved,
                deaths == 1 ? "once" : deaths == 2 ? "twice" : deaths + " times",
                BehaviourWording.gold(f.get("savingGoldHeldAtDeath")));
    return ItemWording.figure(f, "savingLostKnown") == 0
        ? died
        : died + " (lost %s)".formatted(BehaviourWording.gold(f.get("savingGoldLost")));
  }
}
