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

  private static final char VARIATION_SELECTOR = '\uFE0F';
  private static final char ZERO_WIDTH_JOINER = '\u200D';

  private IconText() {}

  /** The text without the icon prefix {@link IconService} puts first; any other text is kept. */
  public static String withoutLeadingIcon(String text) {
    return text == null ? null : LEADING_ICON.matcher(text).replaceFirst("");
  }

  /**
   * The end of a cut at {@code end} moved back so it never splits an emoji tag ({@code
   * <:name:id>}), a surrogate pair, or a character from the variation selector or zero-width joiner
   * that follows it; {@code end} itself when nothing is split.
   */
  public static int safeEnd(String text, int end) {
    int at = Math.min(Math.max(end, 0), text.length());
    int previous;
    do {
      previous = at;
      at = beforeTag(text, at);
      at = beforeUnicodeSequence(text, at);
    } while (at != previous && at > 0);
    return at;
  }

  /**
   * Where to hard-split text at {@code max}: before a tag or sequence the split would break. When
   * the first unit alone is wider than {@code max} it is split after that whole unit, so the split
   * always makes progress and never leaves a lone surrogate.
   */
  public static int splitIndex(String text, int max) {
    int at = safeEnd(text, max);
    return at == 0 ? firstUnitEnd(text) : at;
  }

  private static int firstUnitEnd(String text) {
    if (text.isEmpty()) {
      return 0;
    }
    int end = isTagStart(text, 0) && text.indexOf('>') > 0 ? text.indexOf('>') + 1 : 1;
    while (end < text.length() && continuesSequence(text, end)) {
      end++;
    }
    return end;
  }

  private static int beforeTag(String text, int at) {
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

  private static int beforeUnicodeSequence(String text, int at) {
    while (at > 0 && at < text.length() && continuesSequence(text, at)) {
      boolean pairBefore = at >= 2 && isPair(text, at - 2);
      at -= pairBefore ? 2 : 1;
    }
    return at;
  }

  private static boolean continuesSequence(String text, int at) {
    char next = text.charAt(at);
    return isPair(text, at - 1)
        || next == VARIATION_SELECTOR
        || next == ZERO_WIDTH_JOINER
        || text.charAt(at - 1) == ZERO_WIDTH_JOINER;
  }

  private static boolean isPair(String text, int high) {
    return Character.isHighSurrogate(text.charAt(high))
        && high + 1 < text.length()
        && Character.isLowSurrogate(text.charAt(high + 1));
  }

  private static boolean isTagStart(String text, int at) {
    return text.startsWith("<:", at) || text.startsWith("<a:", at);
  }
}
