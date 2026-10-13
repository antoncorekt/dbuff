package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** A total that is known at each full minute, read at any second by a straight line between. */
public final class MinuteSeries {
  private final TreeMap<Integer, Long> byMinute = new TreeMap<>();

  public void put(Integer minute, Long value) {
    if (minute != null && value != null) {
      byMinute.put(minute, value);
    }
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

  /** One series per player slot out of the stored timeline, by the value picked. */
  public static Map<Long, MinuteSeries> bySlot(
      List<PlayerTimelineDomain> timeline, Function<PlayerTimelineDomain, Long> value) {
    Map<Long, MinuteSeries> bySlot = new HashMap<>();
    for (PlayerTimelineDomain t : timeline) {
      if (t.getPlayerSlot() != null) {
        bySlot
            .computeIfAbsent(t.getPlayerSlot(), k -> new MinuteSeries())
            .put(t.getGameMinute(), value.apply(t));
      }
    }
    return bySlot;
  }
}
