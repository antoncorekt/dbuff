package com.ako.dbuff.service.replay.analysis.lane;

import java.util.Map;
import java.util.TreeMap;

/** A total that is known at each full minute, read at any second by a straight line between. */
public final class MinuteSeries {
  private final TreeMap<Integer, Long> byMinute;

  public MinuteSeries(Map<Integer, Long> totals) {
    this.byMinute = new TreeMap<>(totals);
  }

  public boolean isEmpty() {
    return byMinute.isEmpty();
  }

  public double at(double second) {
    double minute = second / 60.0;
    Map.Entry<Integer, Long> before = byMinute.floorEntry((int) Math.floor(minute));
    Map.Entry<Integer, Long> after = byMinute.ceilingEntry((int) Math.ceil(minute));
    if (before == null) {
      return after.getValue();
    }
    if (after == null || after.getKey().equals(before.getKey())) {
      return before.getValue();
    }
    double part = (minute - before.getKey()) / (after.getKey() - before.getKey());
    return before.getValue() + part * (after.getValue() - before.getValue());
  }

  /** The gain between two seconds; null for a series with no points. */
  public Double gain(double from, double to) {
    return isEmpty() ? null : Math.max(0, at(to) - at(from));
  }

  /**
   * True when the minutes known reach from {@code from} to {@code to}, so no reading is a guess.
   */
  public boolean covers(double from, double to) {
    return !isEmpty() && byMinute.firstKey() * 60.0 <= from && byMinute.lastKey() * 60.0 >= to;
  }
}
