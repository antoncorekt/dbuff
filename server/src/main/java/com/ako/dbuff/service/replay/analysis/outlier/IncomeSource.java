package com.ako.dbuff.service.replay.analysis.outlier;

import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Where a hero's gold came from, by the cumulative per-minute counters the replay minute extractor
 * stores. Passive income, shared and reliable gold are not sources told apart here; a counter the
 * replay lacks counts as zero.
 */
public enum IncomeSource {
  LANE_CREEPS("lane creeps", m -> m.getCreepKillGold()),
  JUNGLE_CREEPS("jungle creeps", m -> m.getNeutralKillGold()),
  KILLS_AND_ASSISTS("kills and assists", m -> m.getHeroKillGold()),
  BUILDINGS("buildings", m -> m.getBuildingGold()),
  BOUNTY_AND_OTHER(
      "bounties and other gold",
      m -> sum(m.getBountyGold(), m.getRoshanGold(), m.getComebackGold(), m.getOtherGold()));

  private final String phrase;
  private final Function<ReplayPlayerMinuteDomain, Long> counter;

  IncomeSource(String phrase, Function<ReplayPlayerMinuteDomain, Long> counter) {
    this.phrase = phrase;
    this.counter = counter;
  }

  public String phrase() {
    return phrase;
  }

  /** The source that gave most, with its share of everything the sources gave. */
  public record Main(IncomeSource source, double share) {}

  /**
   * The largest source between the rows of two game minutes (the latest row at or before each); a
   * tie goes to the first in declaration order.
   *
   * @return empty without a row at the start, or when no source gave anything
   */
  static Optional<Main> main(List<ReplayPlayerMinuteDomain> rows, int fromMinute, int toMinute) {
    TreeMap<Integer, ReplayPlayerMinuteDomain> byMinute = new TreeMap<>();
    rows.stream()
        .filter(r -> r != null && r.getGameMinute() != null)
        .forEach(r -> byMinute.put(r.getGameMinute(), r));
    var from = byMinute.floorEntry(fromMinute);
    var to = byMinute.floorEntry(toMinute);
    if (from == null || to == null || to.getKey() <= from.getKey()) {
      return Optional.empty();
    }
    long total = 0;
    long[] gained = new long[values().length];
    for (IncomeSource s : values()) {
      gained[s.ordinal()] = Math.max(0, value(s, to.getValue()) - value(s, from.getValue()));
      total += gained[s.ordinal()];
    }
    if (total <= 0) {
      return Optional.empty();
    }
    final long sum = total;
    return Arrays.stream(values())
        .max(
            Comparator.<IncomeSource>comparingLong(s -> gained[s.ordinal()])
                .thenComparing(Comparator.comparingInt(IncomeSource::ordinal).reversed()))
        .map(s -> new Main(s, (double) gained[s.ordinal()] / sum));
  }

  private static long value(IncomeSource source, ReplayPlayerMinuteDomain row) {
    Long v = source.counter.apply(row);
    return v == null ? 0 : v;
  }

  private static Long sum(Long... values) {
    long total = 0;
    boolean any = false;
    for (Long v : values) {
      if (v != null) {
        total += v;
        any = true;
      }
    }
    return any ? total : null;
  }
}
