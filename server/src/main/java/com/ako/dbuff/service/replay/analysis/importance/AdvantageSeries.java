package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;

/**
 * Per-minute gold and experience advantage of Radiant (negative favours Dire) and each team's net
 * worth. Index {@code m} is the game clock {@code m * 60} seconds; between minutes the values are
 * interpolated linearly, outside the series they stay at the first or last value.
 */
public record AdvantageSeries(
    double[] radiantGold, double[] radiantXp, double[] radiantNetWorth, double[] direNetWorth) {
  public static final int MINUTE = 60;

  /** The last second the advantage series covers. */
  public double endSeconds() {
    return (Math.min(radiantGold.length, radiantXp.length) - 1) * (double) MINUTE;
  }

  public double gold(TeamSide team, double seconds) {
    return sign(team) * at(radiantGold, seconds);
  }

  public double xp(TeamSide team, double seconds) {
    return sign(team) * at(radiantXp, seconds);
  }

  public double netWorth(TeamSide team, double seconds) {
    return at(team == TeamSide.RADIANT ? radiantNetWorth : direNetWorth, seconds);
  }

  private static double sign(TeamSide team) {
    return team == TeamSide.RADIANT ? 1 : -1;
  }

  static double at(double[] values, double seconds) {
    double minute = Math.max(0, Math.min(seconds / MINUTE, values.length - 1));
    int low = (int) Math.floor(minute);
    int high = Math.min(low + 1, values.length - 1);
    return values[low] + (values[high] - values[low]) * (minute - low);
  }
}
