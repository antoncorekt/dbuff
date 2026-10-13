package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.map.Zone;

/** The lane a player held, from the side of his own team. */
public enum LanePosition {
  SAFE,
  MID,
  OFF;

  /** Radiant's safe lane is the bottom one, Dire's the top one; the other lane is the offlane. */
  public static LanePosition of(TeamSide team, Zone.Lane lane) {
    if (lane == Zone.Lane.MID) {
      return MID;
    }
    boolean bottom = lane == Zone.Lane.BOTTOM;
    return bottom == (team == TeamSide.RADIANT) ? SAFE : OFF;
  }
}
