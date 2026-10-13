package com.ako.dbuff.service.replay.analysis.rune;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The evidence text of a rune fight reason and its reading back, so the wording of a report does
 * not depend on a second copy of the format.
 *
 * @param rune the rune in words, such as {@code double damage}
 * @param clock the pickup time as {@code m:ss}
 * @param hero the hero who took it
 * @param leadSeconds the fight start less the pickup time; negative when the rune was taken inside
 *     the fight
 */
public record RuneEvidence(String rune, String clock, String hero, int leadSeconds) {
  private static final Pattern FORMAT =
      Pattern.compile(
          "^(.+) rune taken at (\\d+:\\d\\d) by (.+), (-?\\d+) s from the fight start,.*$");

  public String text() {
    return "%s rune taken at %s by %s, %d s from the fight start, active in the fight"
        .formatted(rune, clock, hero, leadSeconds);
  }

  public static Optional<RuneEvidence> parse(String text) {
    Matcher m = text == null ? null : FORMAT.matcher(text.trim());
    if (m == null || !m.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new RuneEvidence(m.group(1), m.group(2), m.group(3), Integer.parseInt(m.group(4))));
  }

  /** {@code 40 s before the fight}, or {@code 12 s into the fight} for a rune taken inside it. */
  public String timing() {
    return leadSeconds >= 0
        ? leadSeconds + " s before the fight"
        : -leadSeconds + " s into the fight";
  }
}
