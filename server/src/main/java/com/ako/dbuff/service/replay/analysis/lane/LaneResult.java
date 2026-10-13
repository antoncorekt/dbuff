package com.ako.dbuff.service.replay.analysis.lane;

/** How a side did in its lane. */
public enum LaneResult {
  WON,
  LOST,
  EVEN,
  /** The other side had no hero in the lane. */
  UNCONTESTED
}
