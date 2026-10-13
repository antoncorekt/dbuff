package com.ako.dbuff.service.replay.analysis.behaviour;

/** How a rotation turned out. */
public enum RotationVerdict {
  /** A kill or a won fight came out of it. */
  GOOD,
  /**
   * No kill and no won fight, and the lane he left was lost, or he did nothing while his income
   * dropped, or a tower of the lane he left fell while he was away.
   */
  WASTED,
  /** The hero died before the trip ended. */
  DIED,
  NEUTRAL
}
