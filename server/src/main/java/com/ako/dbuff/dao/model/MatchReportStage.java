package com.ako.dbuff.dao.model;

/** Where a match thread's header is in its lifecycle. A thread row without one is a legacy row. */
public enum MatchReportStage {
  REQUESTED,
  PARSED,
  REPLAY_PENDING,
  REPLAY_DONE,
  REPLAY_UNAVAILABLE,
  PARSE_TIMED_OUT;

  /**
   * Whether a header in the {@code current} stage may move to this one. A header only moves on: the
   * replay outcomes end it, and a parse timeout is possible only before OpenDota parsed.
   */
  public boolean mayFollow(MatchReportStage current) {
    if (current == null) {
      return false;
    }
    if (this == PARSE_TIMED_OUT) {
      return current == REQUESTED;
    }
    return current != PARSE_TIMED_OUT && rank() > current.rank();
  }

  private int rank() {
    return switch (this) {
      case REQUESTED, PARSE_TIMED_OUT -> 0;
      case PARSED -> 1;
      case REPLAY_PENDING -> 2;
      case REPLAY_DONE, REPLAY_UNAVAILABLE -> 3;
    };
  }
}
