package com.ako.dbuff.service.replay.analysis.behaviour;

/**
 * A hero's income per minute over the trip and a short tail, and over the stretch of the same
 * length just before it.
 */
public record Income(double goldBefore, double goldDuring, double xpBefore, double xpDuring) {
  /** The income during the trip fell to {@code share} of the one before, or below. */
  public boolean dropped(double share) {
    return goldBefore > 0 && goldDuring <= share * goldBefore;
  }
}
