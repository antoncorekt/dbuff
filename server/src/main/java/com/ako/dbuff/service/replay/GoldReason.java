package com.ako.dbuff.service.replay;

/**
 * Why a hero received (or lost) gold, as the combat log's gold reason says. The numbers were
 * checked against the deaths, building kills and rune pickups of four real games; a number not
 * listed was seen but not explained, and reads as {@link #UNKNOWN}.
 */
public enum GoldReason {
  /** Starting gold at -90 s and a few unexplained grants. */
  UNSPECIFIED(0),
  /** Gold lost on dying; the value is negative. */
  DEATH(1),
  /** A tower, barracks or ancient fell; every hero of the team gets a share. */
  BUILDING(11),
  /** The kill itself and every assist. */
  HERO_KILL(12),
  /** A lane creep the hero last-hit. */
  CREEP_KILL(13),
  /** A neutral creep the hero last-hit. */
  NEUTRAL_KILL(14),
  /** Roshan or a Tormentor; the DEATH row at the same second says which. */
  BOSS_KILL(15),
  /** The team's share of a courier kill. */
  COURIER_KILL_TEAM(16),
  /** A bounty rune; every hero of the team gets the same amount. */
  BOUNTY_RUNE(17),
  WARD_KILL(20),
  /** The hero who killed the courier. */
  COURIER_KILL(21),
  UNKNOWN(-1);

  private final int code;

  GoldReason(int code) {
    this.code = code;
  }

  public static GoldReason of(int code) {
    for (GoldReason reason : values()) {
      if (reason.code == code) {
        return reason;
      }
    }
    return UNKNOWN;
  }
}
