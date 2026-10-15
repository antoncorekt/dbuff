package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import java.util.OptionalDouble;

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
   * Percentage points the pair's win rate is above the win rate of its two skills in the games
   * where they were not held together: (wins without) over (games without) of both skills added up.
   * Empty for a pair never seen, or when either skill has no game without the other, since the
   * baseline would then rest on one skill only.
   */
  public static OptionalDouble liftPoints(SkillPickStatDomain pair) {
    if (pair.getKind() != SkillStatKind.SKILL_PAIR || pair.getGames() == 0) {
      return OptionalDouble.empty();
    }
    long firstGames = orZero(pair.getFirstGamesWithout());
    long secondGames = orZero(pair.getSecondGamesWithout());
    if (firstGames <= 0 || secondGames <= 0) {
      return OptionalDouble.empty();
    }
    double baseline =
        (double) (orZero(pair.getFirstWinsWithout()) + orZero(pair.getSecondWinsWithout()))
            / (firstGames + secondGames);
    return OptionalDouble.of(100.0 * ((double) pair.getWins() / pair.getGames() - baseline));
  }

  private static long orZero(Long value) {
    return value == null ? 0 : value;
  }
}
