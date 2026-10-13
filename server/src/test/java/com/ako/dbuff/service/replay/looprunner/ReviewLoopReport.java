package com.ako.dbuff.service.replay.looprunner;

import com.ako.dbuff.dao.model.MatchDomain;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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

  /** A table cell: a pipe or a line break in a value must not split the row. */
  public static String tableCell(String value) {
    return singleLine(value).replace("\\", "\\\\").replace("|", "\\|");
  }

  /** The title line of every topic's report of one match. */
  public static String matchHeader(long matchId, String modeName, MatchDomain m) {
    String date =
        m.getStartTime() == null
            ? "unknown date"
            : Instant.ofEpochSecond(m.getStartTime()).atZone(ZoneOffset.UTC).toLocalDate() + "";
    String winner =
        m.getRadiantWin() == null ? "unknown" : (m.getRadiantWin() ? "Radiant" : "Dire");
    return "# Match %d | %s | %s | Radiant %s : %s Dire | winner %s"
        .formatted(
            matchId,
            modeName,
            date,
            m.getRadiantScore() == null ? "?" : m.getRadiantScore(),
            m.getDireScore() == null ? "?" : m.getDireScore(),
            winner);
  }

  /** The two lines every report opens with after its title: how to read it, and what it found. */
  public static List<String> introduction(String howToRead, String summary) {
    return List.of("**How to read this:** " + howToRead, "", "**Summary:** " + summary);
  }
}
