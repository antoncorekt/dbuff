package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Each team's net worth at every minute of a game, from the per-player timelines. */
public record TeamNetWorth(double[] radiant, double[] dire) {
  private static final int PLAYERS = 10;
  private static final long FIRST_DIRE_SLOT = 128;

  /**
   * Null when a minute lacks a player or a net worth, or the minutes do not run 0, 1, 2 and on
   * without a gap.
   */
  public static TeamNetWorth of(List<PlayerTimelineDomain> rows) {
    Map<Integer, long[]> byMinute = new TreeMap<>();
    for (PlayerTimelineDomain row : rows) {
      if (row.getNetworth() == null || row.getGameMinute() == null || row.getPlayerSlot() == null) {
        return null;
      }
      long[] sums = byMinute.computeIfAbsent(row.getGameMinute(), m -> new long[3]);
      sums[row.getPlayerSlot() >= FIRST_DIRE_SLOT ? 1 : 0] += row.getNetworth();
      sums[2]++;
    }
    double[] radiant = new double[byMinute.size()];
    double[] dire = new double[byMinute.size()];
    int expected = 0;
    for (Map.Entry<Integer, long[]> e : byMinute.entrySet()) {
      long[] sums = e.getValue();
      if (e.getKey() != expected || sums[2] != PLAYERS || sums[0] + sums[1] <= 0) {
        return null;
      }
      radiant[expected] = sums[0];
      dire[expected] = sums[1];
      expected++;
    }
    return expected == 0 ? null : new TeamNetWorth(radiant, dire);
  }

  /** Radiant's lead as a share of both teams' net worth together. */
  public double[] radiantLead() {
    double[] lead = new double[radiant.length];
    for (int i = 0; i < lead.length; i++) {
      lead[i] = (radiant[i] - dire[i]) / (radiant[i] + dire[i]);
    }
    return lead;
  }
}
