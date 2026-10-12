package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;

/**
 * What OpenDota's statistics say a player produced in the whole match; each null when not stored.
 *
 * @param damageTaken taken from enemy heroes
 * @param wardsPlaced observer and sentry wards together
 */
public record PlayerOutput(
    Long heroDamage, Long heroHealing, Long damageTaken, Long lastHits, Long wardsPlaced) {
  public static final PlayerOutput NONE = new PlayerOutput(null, null, null, null, null);

  public static PlayerOutput from(PlayerMatchStatisticDomain stat) {
    return new PlayerOutput(
        stat.getHeroDamage(),
        stat.getHeroHealing(),
        stat.getDamageTaken(),
        stat.getLastHits(),
        sum(stat.getObsPlaced(), stat.getSenPlaced()));
  }

  /** The sum of the stored values; null when none is stored. */
  private static Long sum(Long first, Long second) {
    if (first == null && second == null) {
      return null;
    }
    return (first == null ? 0 : first) + (second == null ? 0 : second);
  }
}
