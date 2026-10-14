package com.ako.dbuff.service.ai.reasons;

import java.util.List;

/** What asking for the reasons of a match came to; only READY and CACHED carry reasons. */
public record ReasonsResult(Status status, List<Reason> reasons) {

  public enum Status {
    READY,
    CACHED,
    SWITCHED_OFF,
    NO_API_KEY,
    NO_TEAM,
    NO_FACTS,
    CALL_LIMIT,
    FAILED
  }

  public ReasonsResult {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  public static ReasonsResult of(Status status) {
    return new ReasonsResult(status, List.of());
  }

  public boolean hasReasons() {
    return status == Status.READY || status == Status.CACHED;
  }
}
