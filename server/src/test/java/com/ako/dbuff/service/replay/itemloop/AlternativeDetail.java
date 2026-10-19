package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.AlternativesAspect;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import java.util.Map;
import java.util.Set;

/** The rows the alternatives aspect adds to any entry: items bought instead, or popular ones. */
final class AlternativeDetail {
  private static final Set<String> LABELS =
      Set.of(AlternativesAspect.ROW, AlternativesAspect.POPULAR_ROW);

  private AlternativeDetail() {}

  static boolean handles(Detail detail) {
    return LABELS.contains(detail.label());
  }

  static String[] cells(Detail detail) {
    String item = LoopWording.words(detail.target());
    if (AlternativesAspect.POPULAR_ROW.equals(detail.label())) {
      return new String[] {
        LoopWording.clock(detail.seconds()), item, "popular for this hero at this point", "-"
      };
    }
    Map<String, Double> f = detail.figures();
    return new String[] {
      LoopWording.clock(detail.seconds()),
      item,
      "bought instead at about the same gold",
      "%d games, win rate %s, lead %s"
          .formatted(
              Math.round(f.getOrDefault(AlternativesAspect.GAMES, 0.0)),
              signed(f.get(AlternativesAspect.WIN_RATE_DELTA), 100, "%"),
              signed(f.get(AlternativesAspect.LEAD_DELTA), 1, " gold"))
    };
  }

  private static String signed(Double value, double scale, String unit) {
    return value == null ? "unknown" : "%+.1f%s".formatted(value * scale, unit);
  }
}
