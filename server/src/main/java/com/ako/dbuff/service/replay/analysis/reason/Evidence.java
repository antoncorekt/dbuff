package com.ako.dbuff.service.replay.analysis.reason;

/** Cuts the evidence text of a reason to what {@code fight_reason.evidence} can hold. */
final class Evidence {
  /** The length of the evidence column. */
  static final int MAX_LENGTH = 1000;

  private static final String ELLIPSIS = "...";

  private Evidence() {}

  static String fit(String text) {
    if (text == null || text.length() <= MAX_LENGTH) {
      return text;
    }
    return text.substring(0, MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
  }
}
