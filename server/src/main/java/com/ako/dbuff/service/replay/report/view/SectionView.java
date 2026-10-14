package com.ako.dbuff.service.replay.report.view;

import java.util.List;
import java.util.Objects;

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
}
