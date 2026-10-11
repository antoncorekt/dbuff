package com.ako.dbuff.service.replay;

/** One player as the replay lists them, before they are tied to an OpenDota slot. */
public record ReplayPlayerFact(
    int replayPlayerId, int teamNum, long steamId, String heroEntityName) {

  private static final long STEAM_ID_OFFSET = 76561197960265728L;

  /** The player's index in the match: replay ids are twice the index. */
  public int index() {
    return replayPlayerId / 2;
  }

  /** Radiant 0-4, Dire 128-132, as OpenDota numbers slots. */
  public long playerSlot() {
    return PlayerSlots.slot(index());
  }

  /** Null for an anonymous player. */
  public Long accountId() {
    return steamId == 0 ? null : steamId - STEAM_ID_OFFSET;
  }
}
