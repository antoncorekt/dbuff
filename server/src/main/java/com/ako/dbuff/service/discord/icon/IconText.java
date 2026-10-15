package com.ako.dbuff.service.discord.icon;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Text-level helpers for the icon prefixes that {@link IconService} puts before names. */
public final class IconText {
  private static final Pattern ICON =
      Pattern.compile(
          "(?:<a?:\\w+:\\d+>|"
              + Arrays.stream(IconKind.values())
                  .map(kind -> Pattern.quote(kind.getPlaceholder()))
                  .reduce((a, b) -> a + "|" + b)
                  .orElseThrow()
              + ") ?");

  private IconText() {}

  /** The text without any icon prefix, as it reads with icons switched off. */
  public static String strip(String text) {
    return text == null ? null : ICON.matcher(text).replaceAll("");
  }

  /**
   * The end of a cut at {@code end} moved back before an emoji tag that the cut would split, so a
   * broken {@code <:name:id>} is never posted; {@code end} itself when no tag is split.
   */
  public static int safeEnd(String text, int end) {
    int open = text.lastIndexOf('<', end - 1);
    if (open < 0 || !isTagStart(text, open)) {
      return end;
    }
    int close = text.indexOf('>', open);
    return close >= 0 && close < end ? end : open;
  }

  private static boolean isTagStart(String text, int at) {
    return text.startsWith("<:", at) || text.startsWith("<a:", at);
  }
}
