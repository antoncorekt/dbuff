package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/** The two figures derived from stored counts; neither is stored. */
public final class SkillStatMath {
  private static final double Z = 1.96;

  private SkillStatMath() {}

  /** Lower end of the 95% Wilson interval of the win rate, for ranking only; 0 with no games. */
  public static double wilsonLowerBound(long wins, long games) {
    if (games <= 0) {
      return 0;
    }
    double n = games;
    double p = (double) wins / n;
    double z2 = Z * Z;
    double centre = p + z2 / (2 * n);
    double spread = Z * Math.sqrt((p * (1 - p) + z2 / (4 * n)) / n);
    return Math.max(0, (centre - spread) / (1 + z2 / n));
  }

  /**
   * Percentage points the pair's win rate is above the average of its two skills' win rates in the
   * games where they were not held together; empty for a pair never seen, or when neither skill has
   * a game without the other.
   */
  public static OptionalDouble liftPoints(SkillPickStatDomain pair) {
    if (pair.getKind() != SkillStatKind.SKILL_PAIR || pair.getGames() == 0) {
      return OptionalDouble.empty();
    }
    OptionalDouble baseline =
        DoubleStream.of(
                rate(pair.getFirstGamesWithout(), pair.getFirstWinsWithout()),
                rate(pair.getSecondGamesWithout(), pair.getSecondWinsWithout()))
            .filter(r -> !Double.isNaN(r))
            .average();
    if (baseline.isEmpty()) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(
        100.0 * (rate(pair.getGames(), pair.getWins()) - baseline.getAsDouble()));
  }

  private static double rate(Long games, Long wins) {
    return games == null || games <= 0 || wins == null ? Double.NaN : (double) wins / games;
  }
}
