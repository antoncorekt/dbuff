package com.ako.dbuff.service.ranking;

import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatMath;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToLongFunction;

public enum RankingSort {
  PICKS,
  WIN_RATE,
  WILSON;

  public static RankingSort parse(String value) {
    if (value == null || value.isBlank()) {
      return PICKS;
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  /** Descending by this sort, ties by pick count descending, then id ascending. */
  public <T> Comparator<T> comparator(
      ToLongFunction<T> wins, ToLongFunction<T> picks, ToLongFunction<T> id) {
    Comparator<T> byPicks = Comparator.comparingLong(picks).reversed();
    Comparator<T> byId = Comparator.comparingLong(id);
    return switch (this) {
      case PICKS -> byPicks.thenComparing(byId);
      case WIN_RATE ->
          Comparator.<T>comparingDouble(t -> rate(wins.applyAsLong(t), picks.applyAsLong(t)))
              .reversed()
              .thenComparing(byPicks)
              .thenComparing(byId);
      case WILSON ->
          Comparator.<T>comparingDouble(
                  t -> SkillStatMath.wilsonLowerBound(wins.applyAsLong(t), picks.applyAsLong(t)))
              .reversed()
              .thenComparing(byPicks)
              .thenComparing(byId);
    };
  }

  private static double rate(long wins, long picks) {
    return picks <= 0 ? 0 : (double) wins / picks;
  }
}
