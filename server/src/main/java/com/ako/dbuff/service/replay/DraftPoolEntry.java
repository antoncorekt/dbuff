package com.ako.dbuff.service.replay;

/**
 * One ability of the Ability Draft pool.
 *
 * @param abilityId OpenDota ability id (the replay stores twice this value)
 * @param pickerReplayPlayerId the player who took it, or -1 when nobody did
 * @param finalSlot position on the hero after the draft: 0-2 regular skills, 3 the ultimate
 */
public record DraftPoolEntry(
    int poolIndex, int abilityId, int pickerReplayPlayerId, int finalSlot) {

  public boolean picked() {
    return pickerReplayPlayerId >= 0;
  }
}
