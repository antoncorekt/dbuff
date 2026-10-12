package com.ako.dbuff.service.replay.analysis.role;

import java.util.Arrays;

/**
 * Gives each player a different role so that the total score is highest. A team is five players, so
 * every assignment is tried; a tie goes to the first in order of role, which keeps results stable.
 */
final class RoleAssignmentSolver {
  private static final double EPSILON = 1e-9;

  private RoleAssignmentSolver() {}

  /**
   * @param roleOf the chosen role index (0 based) of each player
   * @param margin how much worse the best assignment is with the player in any other role; 0 when
   *     another role fits him as well
   */
  record Solution(int[] roleOf, double[] margin) {}

  /**
   * @param score {@code score[player][role]}, a square matrix
   */
  static Solution solve(double[][] score) {
    int n = score.length;
    double[][] bestWith = new double[n][n];
    for (double[] row : bestWith) {
      Arrays.fill(row, Double.NEGATIVE_INFINITY);
    }
    Search search = new Search(score, bestWith);
    search.extend(0, 0, new boolean[n]);
    double[] margin = new double[n];
    for (int player = 0; player < n; player++) {
      double other = Double.NEGATIVE_INFINITY;
      for (int role = 0; role < n; role++) {
        if (role != search.best[player]) {
          other = Math.max(other, bestWith[player][role]);
        }
      }
      margin[player] = n == 1 ? 0 : Math.max(0, search.bestTotal - other);
    }
    return new Solution(search.best, margin);
  }

  private static final class Search {
    private final double[][] score;
    private final double[][] bestWith;
    private final int[] current;
    private final int[] best;
    private double bestTotal = Double.NEGATIVE_INFINITY;

    Search(double[][] score, double[][] bestWith) {
      this.score = score;
      this.bestWith = bestWith;
      this.current = new int[score.length];
      this.best = new int[score.length];
    }

    void extend(int player, double total, boolean[] used) {
      if (player == score.length) {
        if (total > bestTotal + EPSILON) {
          bestTotal = total;
          System.arraycopy(current, 0, best, 0, current.length);
        }
        for (int p = 0; p < current.length; p++) {
          bestWith[p][current[p]] = Math.max(bestWith[p][current[p]], total);
        }
        return;
      }
      for (int role = 0; role < score.length; role++) {
        if (!used[role]) {
          used[role] = true;
          current[player] = role;
          extend(player + 1, total + score[player][role], used);
          used[role] = false;
        }
      }
    }
  }
}
