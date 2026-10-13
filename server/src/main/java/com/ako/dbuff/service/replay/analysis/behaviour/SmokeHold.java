package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * A hero holding the smoke modifier.
 *
 * @param sourceSlot the hero who used the smoke, null when the log does not say
 * @param removeSeconds when the modifier was removed, null when no removal was logged
 * @param durationSeconds the full duration the modifier was given, not positive when unknown
 */
public record SmokeHold(
    long slot, Long sourceSlot, double addSeconds, Double removeSeconds, double durationSeconds) {

  enum State {
    EXPIRED,
    BROKEN,
    UNKNOWN
  }

  /** The removal when one was logged, else the time the duration would end it, else the add. */
  double endSeconds() {
    if (removeSeconds != null) {
      return removeSeconds;
    }
    return durationSeconds > 0 ? addSeconds + durationSeconds : addSeconds;
  }

  /** Unknown without a removal or without a duration to measure the removal against. */
  State state(double slackSeconds) {
    if (removeSeconds == null || durationSeconds <= 0) {
      return State.UNKNOWN;
    }
    return removeSeconds - addSeconds < durationSeconds - slackSeconds
        ? State.BROKEN
        : State.EXPIRED;
  }
}
