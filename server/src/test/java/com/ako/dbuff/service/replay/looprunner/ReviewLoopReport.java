package com.ako.dbuff.service.replay.looprunner;

/** The stand-in report of a match a topic could not report, which is never a diff baseline. */
public final class ReviewLoopReport {
  private static final String NOT_REPORTED = "Not reported: ";

  private ReviewLoopReport() {}

  public static String unavailable(long matchId, String reason) {
    return "# Match %d\n\n%s%s\n".formatted(matchId, NOT_REPORTED, singleLine(reason));
  }

  public static boolean isUnavailable(String report) {
    return report.lines().anyMatch(l -> l.startsWith(NOT_REPORTED));
  }

  public static String reasonOf(String report) {
    return report
        .lines()
        .filter(l -> l.startsWith(NOT_REPORTED))
        .map(l -> l.substring(NOT_REPORTED.length()))
        .findFirst()
        .orElse("");
  }

  /** A value on one line: a line break in it must not split a markdown row. */
  public static String singleLine(String value) {
    return value.replaceAll("\\s*[\\r\\n]+\\s*", " ");
  }
}
