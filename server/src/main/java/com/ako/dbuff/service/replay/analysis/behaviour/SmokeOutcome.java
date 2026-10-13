package com.ako.dbuff.service.replay.analysis.behaviour;

/** What a smoke came to, from its start to {@code smoke-outcome-seconds} after its end. */
public enum SmokeOutcome {
  PAID_OFF("paid off"),
  NOTHING("came to nothing"),
  BACKFIRED("backfired");

  private final String words;

  SmokeOutcome(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
