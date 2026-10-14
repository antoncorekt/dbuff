package com.ako.dbuff.service.replay;

/**
 * One hero of the Ability Draft hero pool.
 *
 * @param heroId OpenDota hero id
 * @param pickerReplayPlayerId the player who took the hero, or -1 when nobody did
 */
public record HeroDraftEntry(int poolIndex, int heroId, int pickerReplayPlayerId) {

  public boolean picked() {
    return pickerReplayPlayerId >= 0;
  }
}
