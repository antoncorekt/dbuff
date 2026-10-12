package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import java.util.List;

/**
 * What the match stored about one player that tells his role.
 *
 * @param laneRole OpenDota's lane role (1 safe lane, 2 mid, 3 offlane, 4 jungle); null when absent
 * @param finalNetWorth null when absent
 * @param netWorthByMinute from minute 0; null when there is no series
 * @param activity empty when no replay activity was stored
 */
public record RolePlayer(
    long slot,
    Long laneRole,
    Long finalNetWorth,
    double[] netWorthByMinute,
    List<ActivitySegment> activity) {
  /** The final net worth, else the last of the series; null when neither is stored. */
  public Double finalWorth() {
    if (finalNetWorth != null) {
      return finalNetWorth.doubleValue();
    }
    return netWorthByMinute == null || netWorthByMinute.length == 0
        ? null
        : netWorthByMinute[netWorthByMinute.length - 1];
  }

  /** The net worth at the minute, or at the last stored one when the game was shorter. */
  public Double worthAt(int minute) {
    if (netWorthByMinute == null || netWorthByMinute.length == 0) {
      return finalWorth();
    }
    return netWorthByMinute[Math.min(minute, netWorthByMinute.length - 1)];
  }
}
