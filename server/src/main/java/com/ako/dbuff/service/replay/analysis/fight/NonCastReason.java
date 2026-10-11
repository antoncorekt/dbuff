package com.ako.dbuff.service.replay.analysis.fight;

/** Why a hero who had an ability or item in a fight did not use it. */
public enum NonCastReason {
  ON_COOLDOWN,
  NO_MANA,
  /** Died in the first seconds of the fight. */
  DEAD_EARLY,
  /** Ready, affordable and alive, yet not used. Range and target are not checked. */
  READY_NOT_CAST
}
