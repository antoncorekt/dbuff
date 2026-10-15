package com.ako.dbuff.service.discord.icon;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Text-level helpers for the icon prefixes that {@link IconService} puts before names. */
public final class IconText {
  private static final Pattern LEADING_ICON =
      Pattern.compile(
          "^(?:<:[hai]_\\w{1,30}:\\d+>|"
              + Arrays.stream(IconKind.values())
                  .map(kind -> Pattern.quote(kind.getPlaceholder()))
                  .reduce((a, b) -> a + "|" + b)
                  .orElseThrow()
              + ") ");

  private IconText() {}

  /** The text without the icon prefix {@link IconService} puts first; any other text is kept. */
  public static String withoutLeadingIcon(String text) {
    return text == null ? null : LEADING_ICON.matcher(text).replaceFirst("");
  }

  /**
   * The end of a cut at {@code end} moved back before an emoji tag that the cut would split, so a
   * broken {@code <:name:id>} is never posted; {@code end} itself when no tag is split.
   */
  public static int safeEnd(String text, int end) {
    int at = Math.min(Math.max(end, 0), text.length());
    if (at == 0) {
      return 0;
    }
    int open = text.lastIndexOf('<', at - 1);
    if (open < 0 || !isTagStart(text, open)) {
      return at;
    }
    int close = text.indexOf('>', open);
    return close >= 0 && close < at ? at : open;
  }

  /** Where to hard-split text at {@code max}: before a tag the split would break, never at 0. */
  public static int splitIndex(String text, int max) {
    int at = safeEnd(text, max);
    return at == 0 ? Math.min(max, text.length()) : at;
  }

  private static boolean isTagStart(String text, int at) {
    return text.startsWith("<:", at) || text.startsWith("<a:", at);
  }
}
