package com.ako.dbuff.service.replay.analysis.outlier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/** Finds the stretches in which a per-minute share stayed on one side of a threshold. */
public final class ShareRuns {
  private ShareRuns() {}

  /**
   * @param startMinute first minute of the stretch
   * @param endMinute last minute of the stretch
   * @param extreme the share furthest from the threshold in it
   */
  public record Run(int startMinute, int endMinute, double extreme) {
    public int minutes() {
      return endMinute - startMinute + 1;
    }
  }

  /**
   * @param shares the share at each minute from 0
   * @param holds whether a share counts
   * @param minMinutes the shortest stretch worth returning
   * @param highest whether the extreme is the highest share of the stretch, else the lowest
   */
  public static List<Run> of(
      double[] shares, DoublePredicate holds, int minMinutes, boolean highest) {
    List<Run> runs = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= shares.length; i++) {
      boolean in = i < shares.length && holds.test(shares[i]);
      if (in && start < 0) {
        start = i;
      } else if (!in && start >= 0) {
        if (i - start >= minMinutes) {
          runs.add(new Run(start, i - 1, extreme(shares, start, i - 1, highest)));
        }
        start = -1;
      }
    }
    return runs;
  }

  private static double extreme(double[] shares, int from, int to, boolean highest) {
    double best = shares[from];
    for (int i = from; i <= to; i++) {
      best = highest ? Math.max(best, shares[i]) : Math.min(best, shares[i]);
    }
    return best;
  }
}
