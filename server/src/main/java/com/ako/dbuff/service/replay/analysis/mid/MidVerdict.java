package com.ako.dbuff.service.replay.analysis.mid;

public enum MidVerdict {
  ACTIVE,
  PASSIVE,
  /** Neither rule made him active and the enemy mid or the timeline is missing. */
  UNKNOWN
}
