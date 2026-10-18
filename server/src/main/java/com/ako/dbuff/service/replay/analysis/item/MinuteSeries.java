package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import java.util.List;
import java.util.function.Function;

/**
 * Per-minute amounts from the cumulative counters of the minute rows. A row at minute m holds the
 * totals at the m:00 mark, so the amount of game minute k is the row at k + 1 less the row at k.
 */
final class MinuteSeries {
  private static final double SECONDS_PER_MINUTE = 60;

  private MinuteSeries() {}

  /**
   * The amount in each game minute from 0 to the last row; a minute without both rows or with a
   * missing counter is 0.
   */
  static double[] during(
      List<ReplayPlayerMinuteDomain> rows, Function<ReplayPlayerMinuteDomain, Long> counter) {
    int last = rows.stream().mapToInt(MinuteSeries::minute).max().orElse(0);
    Long[] totals = new Long[Math.max(0, last) + 2];
    for (ReplayPlayerMinuteDomain row : rows) {
      int minute = minute(row);
      if (minute >= 0 && minute < totals.length) {
        totals[minute] = counter.apply(row);
      }
    }
    double[] series = new double[Math.max(0, last)];
    for (int k = 0; k < series.length; k++) {
      if (totals[k] != null && totals[k + 1] != null) {
        series[k] = Math.max(0, totals[k + 1] - totals[k]);
      }
    }
    return series;
  }

  /** Gold earned in a minute, every income counter of the row together. */
  static Long earned(ReplayPlayerMinuteDomain row) {
    return sum(
        row.getHeroKillGold(),
        row.getCreepKillGold(),
        row.getNeutralKillGold(),
        row.getBountyGold(),
        row.getRoshanGold(),
        row.getBuildingGold(),
        row.getCourierGold(),
        row.getComebackGold(),
        row.getIncomeGold(),
        row.getSharedGold(),
        row.getWardKillGold(),
        row.getCreepDenyGold(),
        row.getOtherGold());
  }

  /** Gold lost by dying, as a positive number. */
  static Long lostToDeath(ReplayPlayerMinuteDomain row) {
    return row.getGoldLostToDeath() == null ? null : Math.abs(row.getGoldLostToDeath());
  }

  static int minuteOf(double second) {
    return (int) Math.floor(second / SECONDS_PER_MINUTE);
  }

  static double at(double[] series, double second) {
    int minute = minuteOf(second);
    return minute >= 0 && minute < series.length ? series[minute] : 0;
  }

  private static int minute(ReplayPlayerMinuteDomain row) {
    return row.getGameMinute() == null ? -1 : row.getGameMinute();
  }

  private static Long sum(Long... values) {
    long total = 0;
    for (Long value : values) {
      total += value == null ? 0 : value;
    }
    return total;
  }
}
