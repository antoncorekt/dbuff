package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.discord.icon.IconText;

/** Keeps a line within its length. */
final class LineText {
  static final String ELLIPSIS = "...";

  private LineText() {}

  /** The line cut at the last word boundary that leaves room for the ellipsis. */
  static String cut(String line, int max) {
    if (line.length() <= max) {
      return line;
    }
    int end = max - ELLIPSIS.length();
    int space = line.lastIndexOf(' ', end);
    if (space > 0) {
      end = space;
    } else if (Character.isHighSurrogate(line.charAt(end - 1))) {
      end--;
    }
    return line.substring(0, IconText.safeEnd(line, end)).stripTrailing() + ELLIPSIS;
  }
}
