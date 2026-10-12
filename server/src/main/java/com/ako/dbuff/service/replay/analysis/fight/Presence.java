package com.ako.dbuff.service.replay.analysis.fight;

/** How a player took part in a fight, or why they did not. */
public enum Presence {
  /** Dealt or took hero damage, or died. */
  ACTIVE,
  /** Close to the fight at some moment, but took no part in the damage. */
  PRESENT_PASSIVE,
  ABSENT_DEAD,
  ABSENT_TELEPORTING,
  ABSENT_FARMING,
  ABSENT_BASE,
  ABSENT_PUSHING,
  /** Alive and elsewhere, with nothing the classifier can name. */
  ABSENT_FAR;

  /** Alive and not in the fight. */
  public boolean isAbsentAlive() {
    return this != ACTIVE && this != PRESENT_PASSIVE && this != ABSENT_DEAD;
  }
}
