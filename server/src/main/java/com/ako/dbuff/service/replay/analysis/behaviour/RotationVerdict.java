package com.ako.dbuff.service.replay.analysis.behaviour;

/** How a rotation turned out. */
public enum RotationVerdict {
  /** A kill or a won fight came out of it. */
  GOOD,
  /** No kill and no won fight, and the lane he left lost the creep-kill proxy. */
  WASTED,
  /** The hero died before the trip ended. */
  DIED,
  NEUTRAL
}
