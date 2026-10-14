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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What the whitelisted items (Magic Stick and Wand, Hand of Midas, the farming group) gave their
 * heroes, one short sentence each, verdict first, in the words of the item's {@link
 * ItemKindWording}. The tracked players' rows come first, then the rows furthest from paying
 * exactly their cost (the best and the worst); at most {@code max-item-lines} are listed, those
 * bought before the horn first, then in time order. A row of an unknown kind is skipped quietly, a
 * row that lacks figures its wording needs with one WARN line; a match with no usable row prints
 * nothing. It reads {@code item_efficiency} only, so it is independent of other sections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemsSection implements ReplayReportSection {
  private record Entry(Long id, ItemEfficiency row, ItemKindWording wording) {}

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

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<ItemEfficiencyDomain> stored = itemRepo.findByMatchId(input.matchId());
    if (stored == null) {
      return List.of();
    }
    List<Entry> rows =
        stored.stream().map(row -> usable(input.matchId(), row)).flatMap(Optional::stream).toList();
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
        entry -> entry.wording().shortVerdict(entry.row(), subject(entry.row(), heroes)));
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
        return Optional.of(new Entry(stored.getId(), row, wording.get()));
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
            labels.label("item_" + row.itemKey()),
            bought(row.purchaseSeconds()));
  }

  private static String bought(double seconds) {
    return seconds < 0 ? "bought at the start" : "bought at " + GameTime.clock(seconds);
  }
}
