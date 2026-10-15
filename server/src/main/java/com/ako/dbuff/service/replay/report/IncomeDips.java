package com.ako.dbuff.service.replay.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The rolling gold per minute of a hero from his stored cumulative gold, and the stretches where it
 * fell well below what it had been.
 */
final class IncomeDips {
  static final int WINDOW_MINUTES = 3;
  private static final int LOOKBACK_MINUTES = 6;
  private static final double FALL = 0.6;
  private static final double RECOVERED = 0.8;
  private static final double MIN_DROP = 250;

  private IncomeDips() {}

  /**
   * A stretch of minutes where income stayed below the recovered level.
   *
   * @param fromMinute the first minute whose rolling value was low
   * @param toMinute the last such minute
   * @param before the best rolling value of the minutes before it
   * @param lowest the lowest rolling value in the stretch
   */
  record Dip(int fromMinute, int toMinute, double before, double lowest) {
    double drop() {
      return before - lowest;
    }

    /**
     * The rolling value of a minute covers the minutes before it, so the cause lies up to there.
     */
    double causeFromSeconds() {
      return (fromMinute - WINDOW_MINUTES) * 60.0;
    }

    double toSeconds() {
      return toMinute * 60.0;
    }
  }

  /**
   * Gold per minute over the last {@value #WINDOW_MINUTES} minutes, by minute; NaN where unknown.
   */
  static double[] rolling(Map<Integer, Long> goldByMinute) {
    if (goldByMinute.isEmpty()) {
      return new double[0];
    }
    TreeMap<Integer, Long> gold = new TreeMap<>(goldByMinute);
    int last = gold.lastKey();
    double[] gpm = new double[Math.max(0, last) + 1];
    for (int m = 0; m < gpm.length; m++) {
      Long now = gold.get(m);
      Long then = gold.get(m - WINDOW_MINUTES);
      boolean known = now != null && then != null && now >= then;
      gpm[m] = known ? (now - then) / (double) WINDOW_MINUTES : Double.NaN;
    }
    return gpm;
  }

  /**
   * The stretches of low income, in time order. A fall is measured against the best rolling value
   * from windows that end before the window of the low minute begins, and only from minutes after
   * the previous dip ended, so a dip never compares with samples it overlaps or with a peak from
   * before a recovered dip.
   */
  static List<Dip> find(double[] gpm) {
    List<Dip> dips = new ArrayList<>();
    int floor = 0;
    int m = 1;
    while (m < gpm.length) {
      double peak = peak(gpm, floor, m - WINDOW_MINUTES);
      if (Double.isNaN(gpm[m]) || Double.isNaN(peak) || !fallen(gpm[m], peak)) {
        m++;
        continue;
      }
      int to = m;
      double lowest = gpm[m];
      int k = m + 1;
      while (k < gpm.length && !Double.isNaN(gpm[k]) && gpm[k] < peak * RECOVERED) {
        lowest = Math.min(lowest, gpm[k]);
        to = k;
        k++;
      }
      dips.add(new Dip(m, to, peak, lowest));
      floor = to + 1;
      m = to + 1;
    }
    return dips;
  }

  private static boolean fallen(double value, double peak) {
    return value <= peak * FALL && peak - value >= MIN_DROP;
  }

  private static double peak(double[] gpm, int floor, int last) {
    double peak = Double.NaN;
    for (int i = Math.max(floor, last - LOOKBACK_MINUTES + 1); i <= last; i++) {
      if (i >= 0 && !Double.isNaN(gpm[i]) && (Double.isNaN(peak) || gpm[i] > peak)) {
        peak = gpm[i];
      }
    }
    return peak;
  }
}
