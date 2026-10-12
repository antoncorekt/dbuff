package com.ako.dbuff.service.replay.analysis.role;

/** The two teams of a match; the stored player slots 0-4 are Radiant and 128-132 are Dire. */
public enum TeamSide {
  RADIANT,
  DIRE;

  public static final long FIRST_DIRE_SLOT = 128;

  public static TeamSide ofSlot(long playerSlot) {
    return playerSlot >= FIRST_DIRE_SLOT ? DIRE : RADIANT;
  }
}
