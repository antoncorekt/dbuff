package com.ako.dbuff.service.replay.report.view;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.springframework.stereotype.Component;

/**
 * A view as Discord Components V2: one container in the accent colour with the title and verdict, a
 * divider and a text block per bullet. A view that does not fit the message limits is split over
 * several messages that repeat the title with its page, and an item longer than a message is cut
 * with an ellipsis.
 */
@Component
public class ComponentsViewRenderer implements ViewRenderer {
  private static final String ELLIPSIS = "…";
  private static final String BULLET = "- ";
  private static final String FOOTNOTE = "-# ";
  private static final String PAGE_RESERVE = " (99/99)";
  private static final int CONTAINER_HEADER_AND_DIVIDER = 3;

  @Override
  public List<ReportMessage> render(SectionView view) {
    String verdict = view.verdict() == null ? "" : view.verdict().strip();
    boolean hasHeader = !view.title().isBlank() || !verdict.isEmpty();
    int textBudget =
        MessageLimits.COMPONENTS_TEXT
            - (hasHeader ? header(view.title() + PAGE_RESERVE, verdict).length() : 0);
    List<String> items = items(view, textBudget);
    if (!hasHeader && items.isEmpty()) {
      return List.of();
    }
    List<List<String>> pages = paginate(items, textBudget);
    List<ReportMessage> messages = new ArrayList<>();
    for (int i = 0; i < pages.size(); i++) {
      String title =
          pages.size() > 1 && !view.title().isBlank()
              ? view.title() + " (" + (i + 1) + "/" + pages.size() + ")"
              : view.title();
      messages.add(message(view.accent(), hasHeader ? header(title, verdict) : "", pages.get(i)));
    }
    return messages;
  }

  private static List<String> items(SectionView view, int budget) {
    List<String> items = new ArrayList<>();
    view.bullets().stream()
        .filter(b -> !b.isBlank())
        .forEach(b -> items.add(cut(BULLET + b, budget)));
    if (view.footnote() != null && !view.footnote().isBlank()) {
      items.add(cut(FOOTNOTE + view.footnote(), budget));
    }
    return items;
  }

  private static List<List<String>> paginate(List<String> items, int textBudget) {
    int countBudget = MessageLimits.COMPONENTS_COUNT - CONTAINER_HEADER_AND_DIVIDER;
    List<List<String>> pages = new ArrayList<>();
    List<String> page = new ArrayList<>();
    int length = 0;
    for (String item : items) {
      if (!page.isEmpty()
          && (length + item.length() > textBudget || page.size() + 1 > countBudget)) {
        pages.add(page);
        page = new ArrayList<>();
        length = 0;
      }
      page.add(item);
      length += item.length();
    }
    pages.add(page);
    return pages;
  }

  private static ReportMessage message(Accent accent, String header, List<String> items) {
    List<ContainerChildComponent> children = new ArrayList<>();
    int text = 0;
    if (!header.isEmpty()) {
      children.add(TextDisplay.of(header));
      text += header.length();
    }
    if (!header.isEmpty() && !items.isEmpty()) {
      children.add(Separator.createDivider(Separator.Spacing.SMALL));
    }
    for (String item : items) {
      children.add(TextDisplay.of(item));
      text += item.length();
    }
    List<MessageTopLevelComponent> top =
        List.of(Container.of(children).withAccentColor(accent.rgb()));
    return new ReportMessage.Components(top, text, children.size() + 1);
  }

  private static String header(String title, String verdict) {
    String bold = title.isBlank() ? "" : "**" + title + "**";
    if (verdict.isEmpty()) {
      return bold;
    }
    return bold.isEmpty() ? verdict : bold + "\n" + verdict;
  }

  private static String cut(String text, int max) {
    int limit = Math.max(1, max);
    return text.length() <= limit ? text : text.substring(0, limit - 1) + ELLIPSIS;
  }
}
