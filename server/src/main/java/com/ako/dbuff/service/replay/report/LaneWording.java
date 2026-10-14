package com.ako.dbuff.service.replay.report;

import java.util.List;
import java.util.Locale;

/** The words of the laning stage that the Discord section and the lane loop report share. */
public final class LaneWording {
  /** From this margin a lane was won clearly, and from {@link #BIG_MARGIN} by a lot. */
  public static final double CLEAR_MARGIN = 0.3;

  public static final double BIG_MARGIN = 0.6;

  private LaneWording() {}

  /** How clearly a lane was won: "narrowly", "clearly" or "by a lot", from the margin's size. */
  public static String strength(double margin, double clear, double big) {
    double abs = Math.abs(margin);
    return abs >= big ? "by a lot" : abs >= clear ? "clearly" : "narrowly";
  }

  /** {@code A}, {@code A and B}, {@code A, B and C}. */
  public static String joinNames(List<String> names) {
    if (names.size() <= 1) {
      return String.join("", names);
    }
    return String.join(", ", names.subList(0, names.size() - 1))
        + " and "
        + names.get(names.size() - 1);
  }

  /** The text with its first letter in upper case. */
  public static String capitalised(String text) {
    return text.isEmpty()
        ? text
        : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
  }
}
