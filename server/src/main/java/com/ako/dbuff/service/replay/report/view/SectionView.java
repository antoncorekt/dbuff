package com.ako.dbuff.service.replay.report.view;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a report section says, independent of how it is shown. {@code title} may be blank when the
 * bullets carry their own header; {@code verdict} and {@code footnote} may be null.
 */
public record SectionView(
    String title, String verdict, Accent accent, List<String> bullets, String footnote) {

  public SectionView {
    Objects.requireNonNull(accent, "accent");
    title = title == null ? "" : title;
    bullets = bullets == null ? List.of() : List.copyOf(bullets);
  }

  private static final Pattern TITLE_LINE = Pattern.compile("\\*\\*([^*]+)\\*\\*");

  /**
   * An INFO view of text lines; a leading {@code **Title**} line (blanks around it ignored) becomes
   * the title.
   */
  public static SectionView ofLines(List<String> lines) {
    Matcher title = TITLE_LINE.matcher(lines.isEmpty() ? "" : lines.get(0).strip());
    boolean titled = title.matches();
    return new SectionView(
        titled ? title.group(1) : "",
        null,
        Accent.INFO,
        titled ? lines.subList(1, lines.size()) : lines,
        null);
  }
}
