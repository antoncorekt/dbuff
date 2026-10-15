package com.ako.dbuff.service.replay.report;

import java.util.ArrayList;
import java.util.List;

/** Row layout shared by the draft boards. */
final class BoardRows {
  /** A bullet stays under what a Components card text may hold, with room for the title. */
  private static final int BULLET_LIMIT = 3000;

  private BoardRows() {}

  /**
   * The rows as bullets of at most {@link #BULLET_LIMIT} characters, the header on the first one; a
   * list with icons is long, and a bullet over the message limit would lose its end.
   */
  static List<String> bullets(String header, List<String> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<String> bullets = new ArrayList<>();
    StringBuilder current = new StringBuilder(header);
    for (String row : rows) {
      if (current.length() + 1 + row.length() > BULLET_LIMIT && !current.isEmpty()) {
        bullets.add(current.toString());
        current = new StringBuilder();
      }
      if (!current.isEmpty()) {
        current.append('\n');
      }
      current.append(row);
    }
    bullets.add(current.toString());
    return bullets;
  }
}
