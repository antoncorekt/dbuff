package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Gold and experience per minute of each player over a stretch, from the per-minute totals. */
class MidIncome {
  private static final int MINUTE = 60;

  private final Map<Long, Map<Integer, PlayerTimelineDomain>> bySlot = new HashMap<>();

  MidIncome(List<PlayerTimelineDomain> rows) {
    for (PlayerTimelineDomain row : rows) {
      if (row.getPlayerSlot() != null && row.getGameMinute() != null) {
        bySlot
            .computeIfAbsent(row.getPlayerSlot(), k -> new HashMap<>())
            .put(row.getGameMinute(), row);
      }
    }
  }

  Double goldPerMinute(long slot, int fromSeconds, int toSeconds) {
    return rate(slot, fromSeconds, toSeconds, PlayerTimelineDomain::getGold);
  }

  Double xpPerMinute(long slot, int fromSeconds, int toSeconds) {
    return rate(slot, fromSeconds, toSeconds, PlayerTimelineDomain::getXp);
  }

  /** Null when a minute at either end of the stretch is missing. */
  private Double rate(
      long slot, int fromSeconds, int toSeconds, Function<PlayerTimelineDomain, Long> total) {
    Map<Integer, PlayerTimelineDomain> minutes = bySlot.get(slot);
    int from = fromSeconds / MINUTE;
    int to = Math.min(toSeconds / MINUTE, minutes == null ? 0 : lastMinute(minutes));
    if (minutes == null || to <= from) {
      return null;
    }
    Long start = value(minutes.get(from), total);
    Long end = value(minutes.get(to), total);
    return start == null || end == null ? null : (end - start) / (double) (to - from);
  }

  private static int lastMinute(Map<Integer, PlayerTimelineDomain> minutes) {
    return minutes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
  }

  private static Long value(PlayerTimelineDomain row, Function<PlayerTimelineDomain, Long> total) {
    return row == null ? null : total.apply(row);
  }
}
