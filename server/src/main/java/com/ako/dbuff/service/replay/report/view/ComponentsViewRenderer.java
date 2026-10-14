package com.ako.dbuff.service.replay.report.view;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.springframework.stereotype.Component;

/**
 * A view as Discord Components V2: one container in the accent colour with the title and verdict, a
 * divider and a text block per bullet. A view that does not fit the message limits is split over
 * several cards that repeat the title with its page, and an item longer than a message is cut with
 * an ellipsis. Every card keeps room for the "left out" note. {@link #renderAll} packs consecutive
 * cards into one message while they fit.
 */
@Component
@RequiredArgsConstructor
public class ComponentsViewRenderer implements ViewRenderer {
  private static final String ELLIPSIS = "…";
  private static final String BULLET = "- ";
  private static final String FOOTNOTE = "-# ";
  private static final String PAGE_RESERVE = " (99/99)";
  private static final int CONTAINER_HEADER_AND_DIVIDER = 3;

  private final PlainViewRenderer plain;

  @Override
  public List<ReportMessage> render(SectionView view) {
    String title = cut(view.title(), MessageLimits.HEADER_PART);
    String verdict =
        view.verdict() == null ? "" : cut(view.verdict().strip(), MessageLimits.HEADER_PART);
    int textBudget =
        MessageLimits.COMPONENTS_TEXT
            - MessageLimits.NOTE_TEXT_RESERVE
            - header(title + PAGE_RESERVE, verdict).length();
    List<String> bullets =
        view.bullets().stream()
            .filter(b -> !b.isBlank())
            .map(b -> cut(b, textBudget - BULLET.length()))
            .toList();
    String footnote =
        view.footnote() == null || view.footnote().isBlank()
            ? null
            : cut(view.footnote(), textBudget - FOOTNOTE.length());
    if (title.isBlank() && verdict.isEmpty() && bullets.isEmpty() && footnote == null) {
      return List.of();
    }
    List<Page> pages = paginate(bullets, footnote, textBudget);
    List<ReportMessage> messages = new ArrayList<>();
    for (int i = 0; i < pages.size(); i++) {
      String pageTitle = pageTitle(title, i + 1, pages.size());
      messages.add(message(view.accent(), pageTitle, verdict, pages.get(i)));
    }
    return messages;
  }

  /** The cards of the views, consecutive ones packed into one message while they fit. */
  public List<ReportMessage> renderAll(List<SectionView> views) {
    List<ReportMessage.Components> packed = new ArrayList<>();
    for (SectionView view : views) {
      for (ReportMessage card : render(view)) {
        ReportMessage.Components next = (ReportMessage.Components) card;
        int last = packed.size() - 1;
        if (last >= 0 && packed.get(last).fitsWith(next)) {
          packed.set(last, packed.get(last).plus(next));
        } else {
          packed.add(next);
        }
      }
    }
    return List.copyOf(packed);
  }

  private record Page(List<String> bullets, String footnote) {}

  private static String pageTitle(String title, int page, int pages) {
    if (pages == 1) {
      return title;
    }
    String marker = "(" + page + "/" + pages + ")";
    return title.isBlank() ? marker : title + " " + marker;
  }

  private static List<Page> paginate(List<String> bullets, String footnote, int textBudget) {
    int countBudget =
        MessageLimits.COMPONENTS_COUNT
            - MessageLimits.NOTE_COMPONENT_RESERVE
            - CONTAINER_HEADER_AND_DIVIDER;
    List<Page> pages = new ArrayList<>();
    List<String> page = new ArrayList<>();
    int length = 0;
    for (String bullet : bullets) {
      int size = BULLET.length() + bullet.length();
      if (!page.isEmpty() && (length + size > textBudget || page.size() + 1 > countBudget)) {
        pages.add(new Page(page, null));
        page = new ArrayList<>();
        length = 0;
      }
      page.add(bullet);
      length += size;
    }
    if (footnote != null) {
      int size = FOOTNOTE.length() + footnote.length();
      if (!page.isEmpty() && (length + size > textBudget || page.size() + 1 > countBudget)) {
        pages.add(new Page(page, null));
        page = new ArrayList<>();
      }
    }
    pages.add(new Page(page, footnote));
    return pages;
  }

  private ReportMessage.Components message(Accent accent, String title, String verdict, Page page) {
    String header = header(title, verdict);
    List<ContainerChildComponent> children = new ArrayList<>();
    int text = 0;
    if (!header.isEmpty()) {
      children.add(TextDisplay.of(header));
      text += header.length();
    }
    boolean items = !page.bullets().isEmpty() || page.footnote() != null;
    if (!header.isEmpty() && items) {
      children.add(Separator.createDivider(Separator.Spacing.SMALL));
    }
    for (String bullet : page.bullets()) {
      children.add(TextDisplay.of(BULLET + bullet));
      text += BULLET.length() + bullet.length();
    }
    if (page.footnote() != null) {
      children.add(TextDisplay.of(FOOTNOTE + page.footnote()));
      text += FOOTNOTE.length() + page.footnote().length();
    }
    List<MessageTopLevelComponent> top =
        List.of(Container.of(children).withAccentColor(accent.rgb()));
    String fallback =
        String.join(
            "\n",
            plain.lines(new SectionView(title, null, accent, page.bullets(), page.footnote())));
    return new ReportMessage.Components(top, text, children.size() + 1, fallback);
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
