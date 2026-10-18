package com.ako.dbuff.service.replay.analysis.item;

/**
 * A linear fit of a per-minute series over the minutes before a point, extended forward as the
 * expectation the item's effect is measured against. The minutes before the point show the hero's
 * own stage-of-game drift, so what the item adds is what comes above this line.
 */
final class TrendBaseline {
  private static final int MIN_POINTS_FOR_SLOPE = 2;
  private static final int MIN_POINTS_TO_TRUST = 3;

  private final double intercept;
  private final double slope;
  private final int points;

  private TrendBaseline(double intercept, double slope, int points) {
    this.intercept = intercept;
    this.slope = slope;
    this.points = points;
  }

  /**
   * Fits the minutes {@code [atMinute - trendMinutes, atMinute)} of the series (indexed by game
   * minute; minutes outside it are not used). One point gives a flat line, none a line at 0.
   */
  static TrendBaseline fit(double[] series, int atMinute, int trendMinutes) {
    int from = Math.max(0, atMinute - trendMinutes);
    int to = Math.min(atMinute, series.length);
    int n = Math.max(0, to - from);
    if (n == 0) {
      return new TrendBaseline(0, 0, 0);
    }
    double sumX = 0;
    double sumY = 0;
    for (int m = from; m < to; m++) {
      sumX += m;
      sumY += series[m];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;
    if (n < MIN_POINTS_FOR_SLOPE) {
      return new TrendBaseline(meanY, 0, n);
    }
    double covariance = 0;
    double variance = 0;
    for (int m = from; m < to; m++) {
      covariance += (m - meanX) * (series[m] - meanY);
      variance += (m - meanX) * (m - meanX);
    }
    double slope = covariance / variance;
    return new TrendBaseline(meanY - slope * meanX, slope, n);
  }

  /** Whether enough minutes stood behind the fit to trust it. */
  boolean trusted() {
    return points >= MIN_POINTS_TO_TRUST;
  }

  /** The expected amount of one minute, never below 0. */
  double at(int minute) {
    return Math.max(0, intercept + slope * minute);
  }

  /** The expected amount over the minutes {@code [fromMinute, toMinute)}. */
  double total(int fromMinute, int toMinute) {
    double total = 0;
    for (int m = fromMinute; m < toMinute; m++) {
      total += at(m);
    }
    return total;
  }

  /** The expected amount per minute over the minutes {@code [fromMinute, toMinute)}. */
  double mean(int fromMinute, int toMinute) {
    return toMinute > fromMinute ? total(fromMinute, toMinute) / (toMinute - fromMinute) : 0;
  }
}
