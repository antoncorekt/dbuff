package com.ako.dbuff.dao.model;

import java.util.Arrays;
import java.util.List;

/** Where a match thread's header is in its lifecycle. A thread row without one is a legacy row. */
public enum MatchReportStage {
  REQUESTED,
  PARSED,
  REPLAY_PENDING,
  REPLAY_DONE,
  REPLAY_UNAVAILABLE,
  PARSE_TIMED_OUT;

  /**
   * Whether a header in the {@code current} stage may move to this one. A header only moves on: a
   * parse timeout is possible only before OpenDota parsed, and a replay outcome can still follow
   * it. A replay that failed for good can still succeed after a manual retry or a parser bump, so
   * {@code REPLAY_DONE} may follow {@code REPLAY_UNAVAILABLE}, never the other way round.
   */
  public boolean mayFollow(MatchReportStage current) {
    if (current == null) {
      return false;
    }
    if (this == PARSE_TIMED_OUT) {
      return current == REQUESTED;
    }
    if (this == REPLAY_DONE && current == REPLAY_UNAVAILABLE) {
      return true;
    }
    return rank() > current.rank();
  }

  /** The stages a header may be in for this one to follow. */
  public List<MatchReportStage> predecessors() {
    return Arrays.stream(values()).filter(this::mayFollow).toList();
  }

  private int rank() {
    return switch (this) {
      case REQUESTED -> 0;
      case PARSED, PARSE_TIMED_OUT -> 1;
      case REPLAY_PENDING -> 2;
      case REPLAY_DONE, REPLAY_UNAVAILABLE -> 3;
    };
  }
}
