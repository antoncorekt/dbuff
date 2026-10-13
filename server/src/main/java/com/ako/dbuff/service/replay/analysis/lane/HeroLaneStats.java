package com.ako.dbuff.service.replay.analysis.lane;

/**
 * What one hero did inside his laning window.
 *
 * @param creepScore the value-weighted score of his last hits and denies
 * @param xp the experience he gained in the window, null without the per minute series
 * @param gold the gold he gained in the window, null without the per minute series
 * @param level his level when the window ended, 0 when the track does not say
 * @param ultimateSeconds the first sample second he was at the ultimate level, when that was not
 *     after the window ended; null when he was not, or the track does not say
 */
record HeroLaneStats(
    long slot,
    int lastHits,
    int meleeLastHits,
    int rangedLastHits,
    int siegeLastHits,
    int otherLastHits,
    int denies,
    double creepScore,
    Double xp,
    Double gold,
    int level,
    int kills,
    int deaths,
    long damageDealt,
    long damageTaken,
    long towerDamage,
    int stacks,
    Integer ultimateSeconds) {}
