package com.ako.dbuff.service.replay.analysis.role;

/**
 * What OpenDota's statistics say a player produced in the whole match; each null when not stored.
 *
 * @param damageTaken taken from enemy heroes
 * @param wardsPlaced observer and sentry wards together
 */
public record PlayerOutput(
    Long heroDamage, Long heroHealing, Long damageTaken, Long lastHits, Long wardsPlaced) {
  public static final PlayerOutput NONE = new PlayerOutput(null, null, null, null, null);
}
