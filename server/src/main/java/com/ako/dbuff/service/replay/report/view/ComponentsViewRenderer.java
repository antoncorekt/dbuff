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
 * divider and a text block per bullet (an entry of several lines stays one block without a bullet
 * character). A view that does not fit the message limits is split over several cards that repeat
 * the title with its page, and an item longer than a message is cut with an ellipsis. Every card
 * keeps room for the "left out" note. {@link #renderAll} packs consecutive cards into one message
 * while they fit.
 */
@Component
@RequiredArgsConstructor
public class ComponentsViewRenderer implements ViewRenderer {
  private static final String ELLIPSIS = "…";
  private static final String BULLET = "- ";
  private static final String FOOTNOTE = "-# ";

  private final PlainViewRenderer plain;

  private record Item(String raw, String shown) {}

  private record Page(List<Item> bullets, Item footnote) {}

  private record Budget(int text, int components) {}

  @Override
  public List<ReportMessage> render(SectionView view) {
    String title = cut(view.title(), MessageLimits.HEADER_PART);
    String verdict =
        view.verdict() == null ? "" : cut(view.verdict().strip(), MessageLimits.HEADER_PART);
    boolean hasHeader = !title.isBlank() || !verdict.isEmpty();
    Budget single = budget(hasHeader ? header(title, verdict) : null);
    List<Item> bullets = bullets(view, single.text());
    Item footnote = footnote(view, single.text());
    if (!hasHeader && bullets.isEmpty() && footnote == null) {
      return List.of();
    }
    List<Page> pages = paginate(bullets, footnote, single);
    if (pages.size() > 1) {
      Budget split = budget(header(title + MessageLimits.PAGE_MARKER_RESERVE, verdict));
      bullets = bullets(view, split.text());
      footnote = footnote(view, split.text());
      pages = paginate(bullets, footnote, split);
    }
    List<ReportMessage> messages = new ArrayList<>();
    for (int i = 0; i < pages.size(); i++) {
      messages.add(
          message(view.accent(), pageTitle(title, i + 1, pages.size()), verdict, pages.get(i)));
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

  /** What is left of a card for its bullets once the header, if any, is placed. */
  private static Budget budget(String header) {
    int frame =
        MessageLimits.CONTAINER_COMPONENTS + (header == null ? 0 : MessageLimits.HEADER_COMPONENTS);
    return new Budget(
        MessageLimits.CARD_TEXT - (header == null ? 0 : header.length()),
        MessageLimits.CARD_COMPONENTS - frame);
  }

  private static List<Item> bullets(SectionView view, int textBudget) {
    return view.bullets().stream()
        .filter(b -> !b.isBlank())
        .map(b -> item(b, b.contains("\n") ? "" : BULLET, textBudget))
        .toList();
  }

  private static Item footnote(SectionView view, int textBudget) {
    return view.footnote() == null || view.footnote().isBlank()
        ? null
        : item(view.footnote(), FOOTNOTE, textBudget);
  }

  private static Item item(String raw, String prefix, int textBudget) {
    String cut = cut(raw, textBudget - prefix.length());
    return new Item(cut, prefix + cut);
  }

  private static String pageTitle(String title, int page, int pages) {
    if (pages == 1) {
      return title;
    }
    String marker = "(" + page + "/" + pages + ")";
    return title.isBlank() ? marker : title + " " + marker;
  }

  private static List<Page> paginate(List<Item> bullets, Item footnote, Budget budget) {
    List<Page> pages = new ArrayList<>();
    List<Item> page = new ArrayList<>();
    int length = 0;
    List<Item> all = new ArrayList<>(bullets);
    if (footnote != null) {
      all.add(footnote);
    }
    for (Item item : all) {
      int size = item.shown().length();
      if (!page.isEmpty()
          && (length + size > budget.text() || page.size() + 1 > budget.components())) {
        pages.add(split(page, footnote));
        page = new ArrayList<>();
        length = 0;
      }
      page.add(item);
      length += size;
    }
    pages.add(split(page, footnote));
    return pages;
  }

  private static Page split(List<Item> items, Item footnote) {
    boolean ends = footnote != null && !items.isEmpty() && items.get(items.size() - 1) == footnote;
    return ends ? new Page(items.subList(0, items.size() - 1), footnote) : new Page(items, null);
  }

  private ReportMessage.Components message(Accent accent, String title, String verdict, Page page) {
    String header = header(title, verdict);
    List<ContainerChildComponent> children = new ArrayList<>();
    int text = 0;
    if (!header.isEmpty()) {
      children.add(TextDisplay.of(header));
      text += header.length();
    }
    List<Item> shown = new ArrayList<>(page.bullets());
    if (page.footnote() != null) {
      shown.add(page.footnote());
    }
    if (!header.isEmpty() && !shown.isEmpty()) {
      children.add(Separator.createDivider(Separator.Spacing.SMALL));
    }
    for (Item item : shown) {
      children.add(TextDisplay.of(item.shown()));
      text += item.shown().length();
    }
    List<MessageTopLevelComponent> top =
        List.of(Container.of(children).withAccentColor(accent.rgb()));
    String fallback =
        String.join(
            "\n",
            plain.lines(
                new SectionView(
                    title,
                    verdict,
                    accent,
                    page.bullets().stream().map(Item::raw).toList(),
                    page.footnote() == null ? null : page.footnote().raw()),
                true));
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
