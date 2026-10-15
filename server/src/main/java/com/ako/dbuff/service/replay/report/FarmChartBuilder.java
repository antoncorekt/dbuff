package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.report.chart.ChartFormat;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Per hero, the gold of lane creeps and of camps in each minute as stacked bars, with the hero's
 * farming items (Hand of Midas, Battle Fury, Maelstrom, Mjollnir) as labelled lines at their
 * purchase and the verdict on whether each paid back. The tracked heroes are drawn, at most {@code
 * maxHeroes}; with none tracked that farmed, the heroes that farmed most.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmChartBuilder {
  static final String NO_CREEP_KILLS =
      "There is no farm graph for this match: no creep kills with gold were stored.";
  private static final Set<String> FARMING_KINDS = Set.of("farming-group", "hand-of-midas");
  private static final Color LANE = new Color(0x4C78A8);
  private static final Color JUNGLE = new Color(0xE8A33D);
  private static final Color PAID_BACK = new Color(0x1A7F37);
  private static final Color NOT_PAID_BACK = new Color(0xC0392B);
  private static final Color UNKNOWN = Color.GRAY;
  private static final double MINUTE = 60;

  private final CreepKillRepo creepRepo;
  private final PlayerActivitySegmentRepo segmentRepo;
  private final ItemEfficiencyRepo itemRepo;
  private final ReportHeroNames heroNames;
  private final AbilityLabels labels;

  /**
   * What to say and draw: the {@code spec} is null when there is nothing to draw (the match has no
   * stored creep kills), and then the {@code lines} are all there is.
   */
  public record FarmChart(ChartSpec spec, String verdict, List<String> lines) {}

  /** Empty when the match has neither creep kills nor farming items stored. */
  public Optional<FarmChart> build(long matchId, Set<Long> focusSlots, int maxHeroes) {
    Map<Long, String> names = heroNames.bySlot(matchId);
    Map<Long, List<ItemEfficiency>> items = farmingItems(matchId);
    Map<Long, HeroFarm> farms = farms(matchId);
    if (farms.isEmpty()) {
      return noCreepKills(items, focusSlots, names, maxHeroes);
    }
    Map<Long, List<PlayerActivitySegmentDomain>> segments = segments(matchId);
    Chosen chosen = choose(farms, items, focusSlots, maxHeroes);
    List<String> sentences = new ArrayList<>();
    List<ChartSpec.Panel> panels = new ArrayList<>();
    Map<Long, List<ChartSpec.Marker>> markers = new HashMap<>();
    int minutes = 1;
    for (HeroFarm farm : chosen.heroes()) {
      List<ChartSpec.Marker> own = markers(items.getOrDefault(farm.slot(), List.of()));
      markers.put(farm.slot(), own);
      minutes = Math.max(minutes, farm.lane().length);
      for (ChartSpec.Marker marker : own) {
        minutes = Math.max(minutes, (int) (marker.seconds() / MINUTE) + 1);
      }
    }
    minutes = Math.min(minutes, HeroFarm.MAX_MINUTES);
    Set<Color> used = new HashSet<>();
    for (HeroFarm farm : chosen.heroes()) {
      List<ItemEfficiency> heroItems = items.getOrDefault(farm.slot(), List.of());
      String name = name(names, farm.slot());
      sentences.add(sentence(name, farm, segments.getOrDefault(farm.slot(), List.of()), heroItems));
      markers.get(farm.slot()).forEach(m -> used.add(m.color()));
      panels.add(panel(name, farm, minutes, markers.get(farm.slot())));
    }
    List<ChartSpec.LegendEntry> legend = new ArrayList<>();
    legend.add(new ChartSpec.LegendEntry(LANE, "Lane creeps"));
    legend.add(new ChartSpec.LegendEntry(JUNGLE, "Camps"));
    if (used.contains(PAID_BACK)) {
      legend.add(new ChartSpec.LegendEntry(PAID_BACK, "Item paid back"));
    }
    if (used.contains(NOT_PAID_BACK)) {
      legend.add(new ChartSpec.LegendEntry(NOT_PAID_BACK, "Item not paid back"));
    }
    if (used.contains(UNKNOWN)) {
      legend.add(new ChartSpec.LegendEntry(UNKNOWN, "Payback unknown"));
    }
    ChartSpec spec =
        new ChartSpec(
            "Farm gold per minute", minutes * MINUTE, panels, List.of(), List.of(), legend);
    return Optional.of(
        new FarmChart(
            spec, chosen.note() + sentences.get(0), sentences.subList(1, sentences.size())));
  }

  private Optional<FarmChart> noCreepKills(
      Map<Long, List<ItemEfficiency>> items,
      Set<Long> focusSlots,
      Map<Long, String> names,
      int maxHeroes) {
    if (items.isEmpty()) {
      return Optional.empty();
    }
    List<Long> tracked = items.keySet().stream().filter(s -> isTracked(focusSlots, s)).toList();
    List<String> lines =
        (tracked.isEmpty() ? items.keySet().stream().toList() : tracked)
            .stream()
                .sorted()
                .limit(Math.max(1, maxHeroes))
                .map(s -> itemSentence(name(names, s), items.get(s)))
                .toList();
    return Optional.of(new FarmChart(null, NO_CREEP_KILLS, lines));
  }

  private static boolean isTracked(Set<Long> focusSlots, long slot) {
    return focusSlots != null && focusSlots.contains(slot);
  }

  private static String name(Map<Long, String> names, long slot) {
    return names.getOrDefault(slot, "Player " + slot);
  }

  private Map<Long, HeroFarm> farms(long matchId) {
    List<CreepKillDomain> rows = creepRepo.findByMatchId(matchId);
    if (rows == null) {
      return Map.of();
    }
    return rows.stream()
        .filter(r -> r != null && r.getPlayerSlot() != null)
        .collect(Collectors.groupingBy(CreepKillDomain::getPlayerSlot))
        .entrySet()
        .stream()
        .map(e -> HeroFarm.of(e.getKey(), e.getValue()))
        .filter(f -> f.totalGold() > 0)
        .collect(Collectors.toMap(HeroFarm::slot, f -> f));
  }

  /** The heroes to draw, and the words to put before the verdict when they are not the tracked. */
  private record Chosen(List<HeroFarm> heroes, String note) {}

  private static Chosen choose(
      Map<Long, HeroFarm> farms,
      Map<Long, List<ItemEfficiency>> items,
      Set<Long> focusSlots,
      int maxHeroes) {
    int cap = Math.max(1, maxHeroes);
    List<HeroFarm> tracked =
        Stream.concat(farms.keySet().stream(), items.keySet().stream())
            .distinct()
            .filter(s -> isTracked(focusSlots, s))
            .sorted()
            .map(s -> farms.getOrDefault(s, HeroFarm.none(s)))
            .limit(cap)
            .toList();
    if (!tracked.isEmpty()) {
      return new Chosen(tracked, "");
    }
    List<HeroFarm> top =
        farms.values().stream()
            .sorted(
                Comparator.comparingDouble(HeroFarm::totalGold)
                    .reversed()
                    .thenComparingLong(HeroFarm::slot))
            .limit(cap)
            .toList();
    return new Chosen(top, FallbackNote.of(focusSlots, "farmed", "the top farmers"));
  }

  private Map<Long, List<PlayerActivitySegmentDomain>> segments(long matchId) {
    List<PlayerActivitySegmentDomain> rows = segmentRepo.findByMatchId(matchId);
    return rows == null
        ? Map.of()
        : rows.stream()
            .filter(r -> r != null && r.getPlayerSlot() != null)
            .collect(Collectors.groupingBy(PlayerActivitySegmentDomain::getPlayerSlot));
  }

  private Map<Long, List<ItemEfficiency>> farmingItems(long matchId) {
    List<ItemEfficiencyDomain> rows = itemRepo.findByMatchId(matchId);
    if (rows == null) {
      return Map.of();
    }
    return rows.stream()
        .filter(r -> r != null && FARMING_KINDS.contains(r.getKind()) && r.getPlayerSlot() != null)
        .map(FarmChartBuilder::read)
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(
                ItemEfficiency::purchaseSeconds, Comparator.nullsLast(Comparator.naturalOrder())))
        .collect(Collectors.groupingBy(ItemEfficiency::slot));
  }

  private static ItemEfficiency read(ItemEfficiencyDomain row) {
    try {
      return ItemEfficiencyJson.fromRow(row);
    } catch (RuntimeException e) {
      log.warn("Item efficiency row {} is unreadable and left out of the farm graph", row.getId());
      return null;
    }
  }

  private ChartSpec.Panel panel(
      String name, HeroFarm farm, int minutes, List<ChartSpec.Marker> markers) {
    double[] seconds = new double[minutes];
    double[] lane = new double[minutes];
    double[] neutral = new double[minutes];
    for (int i = 0; i < minutes; i++) {
      seconds[i] = i * MINUTE;
      lane[i] = i < farm.lane().length ? farm.lane()[i] : 0;
      neutral[i] = i < farm.neutral().length ? farm.neutral()[i] : 0;
    }
    return new ChartSpec.Panel(
        name,
        ChartSpec.ValueFormat.COMPACT,
        List.of(),
        List.of(),
        List.of(),
        List.of(
            new ChartSpec.BarSeries(seconds, lane, MINUTE, LANE),
            new ChartSpec.BarSeries(seconds, neutral, MINUTE, JUNGLE)),
        markers);
  }

  private List<ChartSpec.Marker> markers(List<ItemEfficiency> items) {
    return items.stream()
        .filter(i -> i.purchaseSeconds() != null)
        .map(
            i ->
                new ChartSpec.Marker(
                    Math.max(0, i.purchaseSeconds()),
                    "%s (%s), %s"
                        .formatted(itemName(i), ChartFormat.clock(i.purchaseSeconds()), payback(i)),
                    colour(i)))
        .toList();
  }

  private static Color colour(ItemEfficiency item) {
    if (item.paybackSeconds() != null) {
      return PAID_BACK;
    }
    return item.ratio() == null ? UNKNOWN : NOT_PAID_BACK;
  }

  private static String payback(ItemEfficiency item) {
    if (item.paybackSeconds() != null) {
      return "paid back at " + ChartFormat.clock(item.paybackSeconds());
    }
    return item.ratio() == null ? "payback unknown" : "not paid back";
  }

  private String itemName(ItemEfficiency item) {
    return labels.label("item_" + item.itemKey());
  }

  private String sentence(
      String name,
      HeroFarm farm,
      List<PlayerActivitySegmentDomain> segments,
      List<ItemEfficiency> items) {
    if (farm.totalGold() <= 0) {
      return itemSentence(name, items);
    }
    String where =
        switch (farm.mix()) {
          case JUNGLE -> {
            OptionalInt from = farm.jungleFrom(segments);
            yield "farmed mostly in the jungle"
                + (from.isPresent() ? " from " + ChartFormat.clock(from.getAsInt()) : "");
          }
          case LANE -> "farmed mostly on the lanes";
          case SPLIT -> "split farm between the lanes and the jungle";
        };
    String itemPart = items.isEmpty() ? "" : "; " + itemList(items);
    return String.format(
        Locale.ROOT,
        "%s %s (%s lane gold, %s camp gold)%s.",
        name,
        where,
        ChartFormat.compact(farm.laneGold()),
        ChartFormat.compact(farm.neutralGold()),
        itemPart);
  }

  private String itemSentence(String name, List<ItemEfficiency> items) {
    return name + ": " + itemList(items) + ".";
  }

  private String itemList(List<ItemEfficiency> items) {
    return items.stream()
        .map(i -> itemName(i) + " " + payback(i))
        .collect(Collectors.joining(", "));
  }
}
