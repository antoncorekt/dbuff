package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * A hero holding the smoke modifier.
 *
 * @param removeSeconds when the modifier was removed, null when the log ends with it still on
 * @param durationSeconds the full duration the modifier was given
 */
public record SmokeHold(
    long slot, double addSeconds, Double removeSeconds, double durationSeconds) {

  /** When the modifier ended, by the log or, without a removal, by running out. */
  double endSeconds() {
    return removeSeconds == null ? addSeconds + durationSeconds : removeSeconds;
  }

  boolean brokenBefore(double slackSeconds) {
    return endSeconds() - addSeconds < durationSeconds - slackSeconds;
  }
}
