package com.ako.dbuff.dao.model;

/** Where a match thread's header is in its lifecycle. A thread row without one is a legacy row. */
public enum MatchReportStage {
  REQUESTED,
  PARSED,
  REPLAY_PENDING,
  REPLAY_DONE,
  REPLAY_UNAVAILABLE,
  PARSE_TIMED_OUT
}
