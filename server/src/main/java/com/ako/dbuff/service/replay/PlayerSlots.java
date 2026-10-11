package com.ako.dbuff.service.replay;

/** OpenDota numbers slots 0-4 for Radiant and 128-132 for Dire; the replay lists players 0-9. */
public final class PlayerSlots {
  private static final int TEAM_SIZE = 5;
  private static final int DIRE_FIRST_SLOT = 128;

  private PlayerSlots() {}

  public static long slot(int playerIndex) {
    return playerIndex < TEAM_SIZE ? playerIndex : DIRE_FIRST_SLOT + playerIndex - TEAM_SIZE;
  }

  public static int index(long slot) {
    return slot >= DIRE_FIRST_SLOT ? (int) (slot - DIRE_FIRST_SLOT) + TEAM_SIZE : (int) slot;
  }
}
