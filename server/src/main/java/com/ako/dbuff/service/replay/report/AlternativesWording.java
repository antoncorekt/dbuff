package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.AlternativesAspect;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The line a row's alternatives add to its verdict, whatever the row's main aspect. */
public final class AlternativesWording {
  private AlternativesWording() {}

  /**
   * For example {@code ; same gold usually goes to Maelstrom (14 games, +6% win rate) or Manta (few
   * games)}; empty when the row has no alternatives.
   *
   * @param itemName the item's display name by key; null for an unknown item
   */
  public static String suffix(ItemEfficiency row, Function<String, String> itemName) {
    List<Detail> alternatives =
        row.rows().stream()
            .filter(
                d -> d != null && AlternativesAspect.ROW.equals(d.label()) && d.target() != null)
            .toList();
    if (alternatives.isEmpty()) {
      return "";
    }
    return "; same gold usually goes to "
        + alternatives.stream().map(d -> one(d, itemName)).collect(Collectors.joining(" or "));
  }

  private static String one(Detail detail, Function<String, String> itemName) {
    String name = itemName.apply(detail.target());
    String shown = name == null ? detail.target().replace('_', ' ') : name;
    Double delta = detail.figures().get(AlternativesAspect.WIN_RATE_DELTA);
    if (delta == null) {
      return shown + " (few games)";
    }
    return "%s (%d games, %+d%% win rate)"
        .formatted(
            shown,
            Math.round(ItemWording.figure(detail.figures(), AlternativesAspect.GAMES)),
            Math.round(delta * 100));
  }
}
