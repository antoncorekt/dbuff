package com.ako.dbuff.service.replay.report.view;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The plain text of a view: a bold title line, then the bullets as they are, then the footnote. The
 * verdict is left out, so a section's text stays what it was before views existed. Splitting to the
 * message limit stays with the report formatter.
 */
@Component
public class PlainViewRenderer implements ViewRenderer {

  public List<String> lines(SectionView view) {
    List<String> lines = new ArrayList<>();
    if (!view.title().isBlank()) {
      lines.add("**" + view.title() + "**");
    }
    lines.addAll(view.bullets());
    if (view.footnote() != null && !view.footnote().isBlank()) {
      lines.add(view.footnote());
    }
    return lines;
  }

  @Override
  public List<ReportMessage> render(SectionView view) {
    List<String> lines = lines(view);
    return lines.isEmpty() ? List.of() : List.of(new ReportMessage.Plain(String.join("\n", lines)));
  }
}
