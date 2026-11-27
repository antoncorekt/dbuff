package com.ako.dbuff.service.replay;

/** The damage type of a DAMAGE combat-log event; UNKNOWN for other kinds and unmapped values. */
public enum DamageType {
  PHYSICAL,
  MAGICAL,
  PURE,
  UNKNOWN;

  public static DamageType of(int dotaValue) {
    return switch (dotaValue) {
      case 1 -> PHYSICAL;
      case 2 -> MAGICAL;
      case 4 -> PURE;
      default -> UNKNOWN;
    };
  }
}
