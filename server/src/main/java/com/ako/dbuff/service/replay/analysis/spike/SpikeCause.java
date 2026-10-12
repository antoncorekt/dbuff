package com.ako.dbuff.service.replay.analysis.spike;

/** What most of a swing in net worth is put down to. */
public enum SpikeCause {
  /** Kills in a fight the favoured team won. */
  FIGHT_WIN,
  /** Kills outside any fight. */
  PICKOFF,
  OBJECTIVE,
  ROSHAN_AEGIS,
  /** The other team spent its gold on buybacks. */
  BUYBACK_SWING,
  CARRY_FARM,
  COMEBACK_GOLD,
  OTHER
}
