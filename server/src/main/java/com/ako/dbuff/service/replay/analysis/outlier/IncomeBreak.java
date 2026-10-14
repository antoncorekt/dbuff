package com.ako.dbuff.service.replay.analysis.outlier;

import java.util.List;
import java.util.OptionalInt;

/**
 * Finds the minute from which a hero's income broke away from the rest of his team's: the first
 * minute of the first stretch of {@code minutes} in a row in which his net worth gain differed from
 * the teammates' average gain by at least {@code share} of that average (at least 1 gold), upwards
 * or downwards. Of the stretches that overlap the outlier window the one that starts latest counts,
 * so the break is the one that led into the window; a stretch that ended before it or started after
 * it does not overlap.
 */
final class IncomeBreak {
  private IncomeBreak() {}

  /**
   * @param own the hero's net worth at each minute from 0
   * @param mates the teammates' net worth at each minute from 0
   * @param rising whether the hero broke upwards, else downwards
   * @param windowStart first minute of the outlier window, inclusive; minute {@code i} is the gain
   *     from minute {@code i} to {@code i + 1}
   * @param windowEnd last minute of the outlier window, inclusive
   * @return the first minute of the break (inclusive index); empty when there is none or no
   *     teammate to compare with
   */
  static OptionalInt find(
      double[] own,
      List<double[]> mates,
      boolean rising,
      int minutes,
      double share,
      int windowStart,
      int windowEnd) {
    int steps = own.length - 1;
    for (double[] mate : mates) {
      steps = Math.min(steps, mate.length - 1);
    }
    if (mates.isEmpty() || steps < 1) {
      return OptionalInt.empty();
    }
    int runStart = -1;
    int found = -1;
    for (int i = 0; i <= steps; i++) {
      boolean broke = i < steps && broke(own, mates, i, rising, share);
      if (broke && runStart < 0) {
        runStart = i;
      } else if (!broke && runStart >= 0) {
        if (i - runStart >= minutes && runStart <= windowEnd && i - 1 >= windowStart) {
          found = runStart;
        }
        runStart = -1;
      }
    }
    return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
  }

  private static boolean broke(
      double[] own, List<double[]> mates, int minute, boolean rising, double share) {
    double rest = 0;
    for (double[] mate : mates) {
      rest += mate[minute + 1] - mate[minute];
    }
    rest /= mates.size();
    double gap = (own[minute + 1] - own[minute]) - rest;
    double needed = share * Math.max(rest, 1);
    return rising ? gap >= needed : -gap >= needed;
  }
}
