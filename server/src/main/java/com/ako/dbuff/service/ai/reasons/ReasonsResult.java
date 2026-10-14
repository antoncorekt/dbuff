package com.ako.dbuff.service.ai.reasons;

import java.util.List;

/**
 * What asking for the reasons of a match came to; only READY and CACHED carry reasons and the model
 * that wrote them.
 */
public record ReasonsResult(Status status, List<Reason> reasons, String model) {

  public enum Status {
    READY,
    CACHED,
    SWITCHED_OFF,
    MODEL_NOT_ALLOWED,
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
    return new ReasonsResult(status, List.of(), null);
  }

  /** Whether a model answered, with no reasons when the facts did not support any. */
  public boolean answered() {
    return status == Status.READY || status == Status.CACHED;
  }
}
