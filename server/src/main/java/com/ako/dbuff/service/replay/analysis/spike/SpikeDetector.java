package com.ako.dbuff.service.replay.analysis.spike;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the stretches in which the net worth difference between the teams moved far and fast: the
 * swings of a game. Works on one value per minute.
 */
public final class SpikeDetector {

  /**
   * @param minGold the smallest swing worth reporting
   * @param minFraction the smallest swing as a share of both teams' net worth together
   * @param maxMinutes the longest a swing may take
   * @param flipMinGold a lead of this size on both sides of a swing makes it a change of direction
   */
  public record Tuning(int minGold, double minFraction, int maxMinutes, int flipMinGold) {}

  /**
   * @param startMinute where the swing began
   * @param endMinute where it ended
   * @param delta how much the difference moved, positive when the first team gained
   * @param leadBefore the difference at the start
   * @param leadAfter the difference at the end
   * @param directionChange whether the lead changed hands, by more than the tuning's flip size
   */
  public record Spike(
      int startMinute,
      int endMinute,
      double delta,
      double leadBefore,
      double leadAfter,
      boolean directionChange) {}

  private final Tuning tuning;

  public SpikeDetector(Tuning tuning) {
    this.tuning = tuning;
  }

  /**
   * @param difference the first team's net worth minus the other's at each minute
   * @param total both teams' net worth together at each minute
   */
  public List<Spike> detect(double[] difference, double[] total) {
    List<Spike> spikes = new ArrayList<>();
    int i = 0;
    while (i < difference.length - 1) {
      int end = swingEnd(difference, total, i);
      if (end < 0) {
        i++;
        continue;
      }
      spikes.add(spike(difference, i, end));
      i = end;
    }
    return spikes;
  }

  /** The last minute of the swing that starts at {@code start}, or -1 when none does. */
  private int swingEnd(double[] difference, double[] total, int start) {
    int found = -1;
    double best = 0;
    for (int j = start + 1; j < difference.length && j <= start + tuning.maxMinutes(); j++) {
      double move = difference[j] - difference[start];
      double needed = Math.max(tuning.minGold(), tuning.minFraction() * total[j]);
      if (Math.abs(move) >= needed
          && Math.abs(move) > best
          && sameWay(move, found, difference, start)) {
        found = j;
        best = Math.abs(move);
      }
    }
    return found < 0 ? -1 : extend(difference, start, found);
  }

  /** A swing goes on for as long as the difference keeps moving the same way. */
  private static int extend(double[] difference, int start, int end) {
    double way = Math.signum(difference[end] - difference[start]);
    int last = end;
    while (last + 1 < difference.length
        && Math.signum(difference[last + 1] - difference[last]) == way) {
      last++;
    }
    return last;
  }

  private static boolean sameWay(double move, int found, double[] difference, int start) {
    return found < 0 || Math.signum(move) == Math.signum(difference[found] - difference[start]);
  }

  /** The swing from the lowest (or highest) point before its end, so it starts where it began. */
  private Spike spike(double[] difference, int from, int end) {
    double way = Math.signum(difference[end] - difference[from]);
    int start = from;
    for (int s = from; s < end; s++) {
      if (way * difference[s] <= way * difference[start]) {
        start = s;
      }
    }
    double before = difference[start];
    double after = difference[end];
    boolean flipped =
        Math.signum(before) != Math.signum(after)
            && Math.abs(before) >= tuning.flipMinGold()
            && Math.abs(after) >= tuning.flipMinGold();
    return new Spike(start, end, after - before, before, after, flipped);
  }
}
