package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.lane.MinuteSeries;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * What the trip cost in income: gold and experience per minute over the trip and a tail after it,
 * against the stretch of the same length before the trip, read off the per-minute totals.
 */
public final class IncomeProxy {
  private static final double SECONDS_PER_MINUTE = 60.0;

  private final int tailSeconds;

  public IncomeProxy(int tailSeconds) {
    this.tailSeconds = tailSeconds;
  }

  /**
   * Empty when a series is missing, or does not reach from the stretch before the trip (which would
   * start before 0:00) to the end of the tail, or when that stretch before is not {@code
   * undisturbed} (another trip of his, or time he was dead), which would give a false low
   * reference.
   */
  public Optional<Income> of(
      AwayTrip trip, MinuteSeries gold, MinuteSeries xp, BiPredicate<Double, Double> undisturbed) {
    if (gold == null || xp == null) {
      return Optional.empty();
    }
    double end = trip.endSeconds() + (trip.end().hasTail() ? tailSeconds : 0);
    double length = end - trip.startSeconds();
    double referenceStart = trip.startSeconds() - length;
    if (length <= 0
        || referenceStart < 0
        || !undisturbed.test(referenceStart, (double) trip.startSeconds())
        || !gold.covers(referenceStart, end)
        || !xp.covers(referenceStart, end)) {
      return Optional.empty();
    }
    return Optional.of(
        new Income(
            perMinute(gold, referenceStart, trip.startSeconds(), length),
            perMinute(gold, trip.startSeconds(), end, length),
            perMinute(xp, referenceStart, trip.startSeconds(), length),
            perMinute(xp, trip.startSeconds(), end, length)));
  }

  private static double perMinute(MinuteSeries series, double from, double to, double length) {
    return series.gain(from, to) * SECONDS_PER_MINUTE / length;
  }
}
