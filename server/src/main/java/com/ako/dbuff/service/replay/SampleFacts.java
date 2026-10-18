package com.ako.dbuff.service.replay;

import java.util.List;

/**
 * Per-player state sampled every {@code stepSeconds} of game time, plus the cumulative counters at
 * each game minute. Times are seconds from 0:00, so the pre-game phase is negative.
 *
 * @param startSeconds game second of the first sample
 */
public record SampleFacts(
    int startSeconds, int stepSeconds, List<PlayerSeries> series, List<MinuteSnapshot> minutes) {

  /**
   * One player's samples, all arrays as long as the sample count.
   *
   * <p>A sample where the hero entity does not exist (before it spawns, or when the game ends) is
   * all zeros, so a level of 0 means "no data".
   *
   * @param mana the mana points, as against {@code manaPercent}
   * @param attackRange the hero's attack range in world units, as the game reports it
   * @param flags bit 0 is set while the hero is alive
   * @param gold reliable plus unreliable gold, or null when the replay has neither property
   * @param maxHp the hero's maximum health, or null when the replay does not have it
   */
  public record PlayerSeries(
      int replayPlayerId,
      int[] x,
      int[] y,
      int[] hpPercent,
      int[] manaPercent,
      int[] mana,
      int[] attackRange,
      int[] level,
      int[] flags,
      int[] gold,
      int[] maxHp) {}

  /**
   * One player's counters at a game minute, indexed by {@link MinuteStat#ordinal()}, with {@link
   * MinuteStat#MISSING} for a field the replay does not have.
   */
  public record MinuteSnapshot(int replayPlayerId, int gameMinute, long[] values) {}
}
