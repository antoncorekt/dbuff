package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;

/** The place of each player of a team by net worth. */
final class NetWorthRanks {
  private NetWorthRanks() {}

  /**
   * Place by worth with 1 the highest and a tie sharing the average place; NaN when unknown, and
   * then he is left out of the places of the others.
   */
  static double[] of(List<Double> worth) {
    double[] ranks = new double[worth.size()];
    for (int i = 0; i < ranks.length; i++) {
      if (worth.get(i) == null) {
        ranks[i] = Double.NaN;
        continue;
      }
      double own = worth.get(i);
      long higher = worth.stream().filter(w -> w != null && w > own).count();
      long equal = worth.stream().filter(w -> w != null && w == own).count();
      ranks[i] = higher + (equal + 1) / 2.0;
    }
    return ranks;
  }

  /**
   * The mean of the final place and the place at the end of the laning phase; either may be NaN.
   */
  static double farmRank(double finalRank, double laningRank, double unknown) {
    if (Double.isNaN(finalRank) && Double.isNaN(laningRank)) {
      return unknown;
    }
    if (Double.isNaN(laningRank)) {
      return finalRank;
    }
    return Double.isNaN(finalRank) ? laningRank : (finalRank + laningRank) / 2;
  }
}
