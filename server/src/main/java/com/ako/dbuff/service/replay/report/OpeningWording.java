package com.ako.dbuff.service.replay.report;

/** The words of the opening of the game that the Discord section and the rune loop report share. */
public final class OpeningWording {
  private static final String UNKNOWN = "?";

  private OpeningWording() {}

  /** The result of a team's opening as a phrase: {@code won the opening}. */
  public static String result(String stored) {
    return switch (stored == null ? "" : stored) {
      case "WON" -> "won the opening";
      case "LOST" -> "lost the opening";
      default -> "drew the opening";
    };
  }

  /** A balance with its sign: {@code +1.2k}, {@code -300}, {@code 0}; {@code ?} when not known. */
  public static String signedGold(Long value) {
    if (value == null) {
      return UNKNOWN;
    }
    return (value > 0 ? "+" : value < 0 ? "-" : "") + BehaviourWording.gold(Math.abs(value));
  }
}
