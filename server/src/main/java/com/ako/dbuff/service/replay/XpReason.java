package com.ako.dbuff.service.replay;

/**
 * Why a hero received experience, as the combat log's experience reason says. Checked against the
 * deaths of four real games; a number not listed was seen but not explained, and reads as {@link
 * #UNKNOWN}.
 */
public enum XpReason {
  UNSPECIFIED(0),
  HERO_KILL(1),
  /** Any creep that died near the hero, last hit or not; neutrals are in here too. */
  CREEP_KILL(2),
  ROSHAN_KILL(3),
  /** Seen at 7:00, 14:00, 21:00 and 28:00 with 200, 500, 800 and 1100 experience. */
  WISDOM_RUNE(4),
  UNKNOWN(-1);

  private final int code;

  XpReason(int code) {
    this.code = code;
  }

  public static XpReason of(int code) {
    for (XpReason reason : values()) {
      if (reason.code == code) {
        return reason;
      }
    }
    return UNKNOWN;
  }
}
