package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the whitelisted items (Magic Stick and Wand, Hand of Midas, the farming group) gave their
 * heroes, one line each: hero, item, when he bought it and the verdict with its numbers in the
 * words of the item loop report. The tracked players' rows come first, then the rows furthest from
 * paying exactly their cost (the best and the worst); at most {@code max-item-lines} are listed, in
 * time order. A row of an unknown kind or without its figures is skipped, and a match with no
 * usable row prints nothing. It reads {@code item_efficiency} only, so it is independent of other
 * sections.
 */
@Component
@RequiredArgsConstructor
public class ItemsSection implements ReplayReportSection {
  private static final String ITEM_PREFIX = "item_";

  private record Entry(Long id, ItemEfficiency row, String verdict) {}

  private static final Comparator<Entry> CHRONOLOGICAL =
      Comparator.comparing(
              (Entry e) -> e.row().purchaseSeconds(),
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(e -> e.row().slot())
          .thenComparing(e -> e.row().entryKey())
          .thenComparing(Entry::id, Comparator.nullsLast(Comparator.naturalOrder()));

  private final ItemEfficiencyRepo itemRepo;
  private final ReportHeroNames heroNames;
  private final AbilityLabels labels;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "items";
  }

  @Override
  public int order() {
    return 75;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<ItemEfficiencyDomain> stored = itemRepo.findByMatchId(input.matchId());
    if (stored == null) {
      return List.of();
    }
    List<Entry> rows = stored.stream().map(ItemsSection::usable).flatMap(Optional::stream).toList();
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    Set<Long> focus = input.focusSlots() == null ? Set.of() : input.focusSlots();
    Comparator<Entry> byWorth =
        Comparator.comparingInt((Entry e) -> focus.contains(e.row().slot()) ? 0 : 1)
            .thenComparing(ItemsSection::distanceFromCost, Comparator.reverseOrder())
            .thenComparing(CHRONOLOGICAL);
    return BehaviourLines.render(
        "**Items**",
        rows,
        byWorth,
        CHRONOLOGICAL,
        properties.getMaxItemLines(),
        properties.getMaxBehaviourLineLength(),
        entry -> line(entry, heroes));
  }

  private static Optional<Entry> usable(ItemEfficiencyDomain stored) {
    if (stored == null || !ItemWording.words(stored.getKind())) {
      return Optional.empty();
    }
    try {
      ItemEfficiency row = ItemEfficiencyJson.fromRow(stored);
      if (row.purchaseSeconds() == null || !ItemWording.complete(row)) {
        return Optional.empty();
      }
      return Optional.of(new Entry(stored.getId(), row, ItemWording.result(row)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static double distanceFromCost(Entry entry) {
    Double ratio = entry.row().ratio();
    return ratio == null || ratio.isNaN() ? -1 : Math.abs(ratio - 1);
  }

  private String line(Entry entry, Map<Long, String> heroes) {
    ItemEfficiency row = entry.row();
    return "%s bought %s at %s: %s"
        .formatted(
            BehaviourWording.hero(heroes, row.slot()),
            labels.label(ITEM_PREFIX + row.itemKey()),
            GameTime.clock(row.purchaseSeconds()),
            entry.verdict());
  }
}
