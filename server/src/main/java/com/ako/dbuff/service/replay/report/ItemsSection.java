package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.item.HeroInputs;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyEntry;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyFile;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What the catalog items gave their heroes, one short sentence each under the heading of its
 * category (the stored one, else the catalog's), verdict first, in the words of the item's {@link
 * ItemKindWording}. The tracked players' rows come first, then the rows furthest from paying
 * exactly their cost (the best and the worst); at most {@code max-item-lines} per category are
 * listed, those bought before the horn first, then in time order. A row of an unknown kind is
 * skipped quietly, a row that lacks figures its wording needs with one WARN line; a match with no
 * usable row prints nothing. It reads {@code item_efficiency} only, so it is independent of other
 * sections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemsSection implements ReplayReportSection {
  private record Entry(Long id, ItemEfficiency row, ItemKindWording wording, String category) {}

  private static final Comparator<Entry> CHRONOLOGICAL =
      Comparator.comparing(
              (Entry e) -> e.row().purchaseSeconds(),
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().slot())
          .thenComparing(e -> e.row().entryKey(), Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(Entry::id, Comparator.nullsLast(Comparator.naturalOrder()));

  private final ItemEfficiencyRepo itemRepo;
  private final ReportHeroNames heroNames;
  private final ItemKindWordings wordings;
  private final ItemEfficiencyFile catalog;
  private final AbilityLabels labels;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "items";
  }

  @Override
  public int order() {
    return 77;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  /** The rows the cap kept and their lines, so the verdict counts what the card lists. */
  private record Rendered(List<Entry> shown, List<String> lines) {}

  @Override
  public List<String> lines(ReplayReportInput input) {
    return render(input, entries(input)).lines();
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    Rendered rendered = render(input, entries(input));
    return SectionViews.titled(rendered.lines(), paidBack(input, rendered.shown()));
  }

  private List<Entry> entries(ReplayReportInput input) {
    List<ItemEfficiencyDomain> stored = itemRepo.findByMatchId(input.matchId());
    if (stored == null) {
      return List.of();
    }
    Optional<List<String>> wanted = wanted(input);
    return stored.stream()
        .map(row -> usable(input.matchId(), row))
        .flatMap(Optional::stream)
        .filter(entry -> wanted.map(names -> isWanted(entry, names)).orElse(true))
        .toList();
  }

  private static Optional<List<String>> wanted(ReplayReportInput input) {
    if (input.itemFilter() != null) {
      return input.itemFilter().isEmpty() ? Optional.empty() : Optional.of(input.itemFilter());
    }
    return input.instance() == null
        ? Optional.empty()
        : input.instance().effectiveSettings().itemCategoryNames();
  }

  private static boolean isWanted(Entry entry, List<String> names) {
    return names.contains(entry.category()) || names.contains(entry.row().entryKey());
  }

  private String categoryOf(ItemEfficiencyDomain stored, ItemEfficiency row) {
    if (stored.getCategory() != null) {
      return stored.getCategory();
    }
    return catalog.entries().stream()
        .filter(
            e ->
                e.key().equals(row.entryKey())
                    || (row.entryKey() == null && e.items().contains(row.itemKey())))
        .map(ItemEfficiencyEntry::category)
        .findFirst()
        .orElse(null);
  }

  private Verdict paidBack(ReplayReportInput input, List<Entry> rows) {
    Set<Long> focus = input.focusSlots() == null ? Set.of() : input.focusSlots();
    List<Entry> own = rows.stream().filter(e -> focus.contains(e.row().slot())).toList();
    List<Entry> scope = own.isEmpty() ? rows : own;
    long known = scope.stream().filter(e -> e.row().ratio() != null).count();
    long paid = scope.stream().filter(e -> e.row().ratio() != null && e.row().ratio() >= 1).count();
    String whose = own.isEmpty() ? "Items shown" : "Your players' items shown";
    if (known == 0) {
      return new Verdict(
          "%s: %s checked".formatted(whose, SectionViews.count(scope.size(), "purchase")),
          Accent.NEUTRAL);
    }
    return new Verdict(
        "%s: %d of %d paid back their cost".formatted(whose, paid, known),
        SectionViews.balance(paid, known - paid));
  }

  private Rendered render(ReplayReportInput input, List<Entry> rows) {
    if (rows.isEmpty()) {
      return new Rendered(List.of(), List.of());
    }
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    Set<Long> focus = input.focusSlots() == null ? Set.of() : input.focusSlots();
    Comparator<Entry> byWorth =
        Comparator.comparingInt((Entry e) -> focus.contains(e.row().slot()) ? 0 : 1)
            .thenComparing(ItemsSection::distanceFromCost, Comparator.reverseOrder())
            .thenComparing(CHRONOLOGICAL);
    Map<String, String> names = catalog.categories();
    List<String> order = new ArrayList<>(names.keySet());
    order.add(null);
    List<Entry> shown = new ArrayList<>();
    List<String> lines = new ArrayList<>();
    for (String category : order) {
      List<Entry> inCategory =
          BehaviourLines.pick(
                  rows.stream().filter(e -> Objects.equals(e.category(), category)).toList(),
                  byWorth,
                  CHRONOLOGICAL,
                  properties.getMaxItemLines())
              .listed();
      shown.addAll(inCategory);
      lines.addAll(
          BehaviourLines.lines(
              names.containsKey(category) ? "**" + names.get(category) + "**" : null,
              inCategory,
              properties.getMaxBehaviourLineLength(),
              entry ->
                  entry.wording().shortVerdict(entry.row(), subject(entry.row(), heroes))
                      + AlternativesWording.suffix(entry.row(), this::itemLabel)));
    }
    if (shown.isEmpty()) {
      return new Rendered(List.of(), List.of());
    }
    lines.add(0, "**Items**");
    return new Rendered(shown, lines);
  }

  private Optional<Entry> usable(long matchId, ItemEfficiencyDomain stored) {
    if (stored == null) {
      return Optional.empty();
    }
    Optional<ItemKindWording> wording = wordings.find(stored.getKind());
    if (wording.isEmpty()) {
      return Optional.empty();
    }
    try {
      ItemEfficiency row = ItemEfficiencyJson.fromRow(stored);
      if (row.purchaseSeconds() != null && wording.get().complete(row)) {
        return Optional.of(new Entry(stored.getId(), row, wording.get(), categoryOf(stored, row)));
      }
      log.warn(
          "Items section skips row {} of match {}: figures missing", stored.getEntryKey(), matchId);
    } catch (RuntimeException e) {
      log.warn(
          "Items section skips row {} of match {}: {}",
          stored.getEntryKey(),
          matchId,
          e.toString());
    }
    return Optional.empty();
  }

  private static double distanceFromCost(Entry entry) {
    Double ratio = entry.row().ratio();
    return ratio == null || ratio.isNaN() ? -1 : Math.abs(ratio - 1);
  }

  private String subject(ItemEfficiency row, Map<Long, String> heroes) {
    return "%s's %s (%s)"
        .formatted(
            BehaviourWording.hero(heroes, row.slot()),
            labels.decorated("item_" + row.itemKey()),
            bought(row.purchaseSeconds()));
  }

  private String itemLabel(String key) {
    return labels.decorated(HeroInputs.replayName(key));
  }

  private static String bought(double seconds) {
    return seconds < 0 ? "bought at the start" : "bought at " + GameTime.clock(seconds);
  }
}
