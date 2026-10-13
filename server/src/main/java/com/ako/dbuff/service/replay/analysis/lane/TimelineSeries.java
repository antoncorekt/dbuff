package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds the per-minute series of each player slot out of the stored timeline. */
public final class TimelineSeries {
  private TimelineSeries() {}

  public static Map<Long, MinuteSeries> bySlot(
      List<PlayerTimelineDomain> timeline, Function<PlayerTimelineDomain, Long> value) {
    Map<Long, Map<Integer, Long>> totals = new HashMap<>();
    for (PlayerTimelineDomain t : timeline) {
      Long total = value.apply(t);
      if (t.getPlayerSlot() != null && t.getGameMinute() != null && total != null) {
        totals
            .computeIfAbsent(t.getPlayerSlot(), k -> new HashMap<>())
            .put(t.getGameMinute(), total);
      }
    }
    Map<Long, MinuteSeries> bySlot = new HashMap<>();
    totals.forEach((slot, byMinute) -> bySlot.put(slot, new MinuteSeries(byMinute)));
    return bySlot;
  }
}
