package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;

/** A match the queue handed out and the lane it came from. */
public record QueuedMatch(MatchDomain match, Lane lane) {
  public enum Lane {
    FRESH,
    YOUNG_RETRY,
    NEW,
    BACKLOG
  }
}
