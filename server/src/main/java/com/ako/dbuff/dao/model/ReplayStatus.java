package com.ako.dbuff.dao.model;

/** Outcome of one replay run; see the replay plan, section 3c.1. */
public enum ReplayStatus {
  RUNNING(false),
  DONE(true),
  PARTIAL(false),
  SKIPPED_NO_REPLAY_INFO(true),
  SKIPPED_NOT_SUPPORTED(true),
  SKIPPED_NOT_PLAYED(true),
  EXPIRED(true),
  TOO_LARGE(true),
  FAILED_IDENTITY(true),
  FAILED_PARSER(true),
  FAILED_DOWNLOAD(false),
  FAILED_RESOURCE(false),
  FAILED_TIMEOUT(false);

  private final boolean permanent;

  ReplayStatus(boolean permanent) {
    this.permanent = permanent;
  }

  /** A permanent status is never retried for the same parser version. */
  public boolean isPermanent() {
    return permanent;
  }

  public boolean isRetryable() {
    return !permanent && this != DONE;
  }
}
