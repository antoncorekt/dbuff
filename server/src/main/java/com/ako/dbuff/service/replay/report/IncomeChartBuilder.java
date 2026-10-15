package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.IncomeDips.Dip;
import com.ako.dbuff.service.replay.report.chart.ChartFormat;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.Pin;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.PinShape;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The income of the tracked heroes (the richest heroes when the channel tracks nobody who has data)
 * over the game, one panel each: the rolling gold per minute, where it fell and why, and what the
 * hero died of, bought back and bought. Everything is read from what the replay analysis stored.
 */
@Component
@RequiredArgsConstructor
public class IncomeChartBuilder {
  private static final int LAST_MINUTE = 600;
  private static final int MIN_POINTS = 3;
  private static final int SECONDS = 60;
  private static final int MAX_DIPS_PER_HERO = 3;

  private final PlayerTimelineRepo timelineRepo;
  private final ReportHeroNames heroNames;
  private final IncomeReasons reasons;
  private final IncomePins pins;

  /** The chart and what it says: the biggest fall first, then one line per fall and per Midas. */
  public record IncomeChart(ChartSpec spec, String verdict, List<String> bullets) {}

  /** Empty when no hero has enough stored minutes of gold to draw. */
  public Optional<IncomeChart> build(
      long matchId, Set<Long> focusSlots, int maxHeroes, int maxFindings, int lineLength) {
    Map<Long, TreeMap<Integer, Long>> gold = goldBySlot(matchId);
    List<Long> slots = chosen(gold, focusSlots == null ? Set.of() : focusSlots, maxHeroes);
    if (slots.isEmpty()) {
      return Optional.empty();
    }
    Map<Long, String> heroes = heroNames.bySlot(matchId);
    IncomeReasons.Explainer why = reasons.read(matchId, heroes);
    IncomePins.Events events = pins.read(matchId);
    List<ChartSpec.Panel> panels = new ArrayList<>();
    List<Pin> allPins = new ArrayList<>();
    List<Found> found = new ArrayList<>();
    List<String> midas = new ArrayList<>();
    double end = SECONDS;
    for (long slot : slots) {
      double[] gpm = IncomeDips.rolling(gold.get(slot));
      List<Dip> dips = biggest(IncomeDips.find(gpm));
      List<Pin> hisPins = events.pins(slot);
      panels.add(panel(slot, heroes, gpm, dips, hisPins));
      allPins.addAll(hisPins);
      dips.forEach(
          d -> found.add(new Found(slot, d, why.why(slot, d.causeFromSeconds(), d.toSeconds()))));
      events.midasSentence(slot, heroes).ifPresent(midas::add);
      end = Math.max(end, (gpm.length - 1) * (double) SECONDS);
    }
    List<Found> worst =
        found.stream()
            .sorted(Comparator.comparingDouble((Found f) -> f.dip().drop()).reversed())
            .toList();
    List<String> bullets = new ArrayList<>();
    worst.stream()
        .skip(1)
        .limit(maxFindings)
        .map(f -> LineText.cut(line(f, heroes), lineLength))
        .forEach(bullets::add);
    midas.stream().map(m -> LineText.cut(m + ".", lineLength)).forEach(bullets::add);
    ChartSpec spec =
        new ChartSpec("Hero income", end, panels, List.of(), List.of(), legend(found, allPins));
    return Optional.of(
        new IncomeChart(spec, LineText.cut(verdict(worst, heroes), lineLength), bullets));
  }

  private static List<Dip> biggest(List<Dip> dips) {
    return dips.stream()
        .sorted(Comparator.comparingDouble(Dip::drop).reversed())
        .limit(MAX_DIPS_PER_HERO)
        .toList();
  }

  private record Found(long slot, Dip dip, List<String> why) {}

  private ChartSpec.Panel panel(
      long slot, Map<Long, String> heroes, double[] gpm, List<Dip> dips, List<Pin> hisPins) {
    double[] seconds = new double[gpm.length];
    for (int m = 0; m < gpm.length; m++) {
      seconds[m] = m * (double) SECONDS;
    }
    Color colour =
        TeamSide.ofSlot(slot) == TeamSide.RADIANT ? ChartPalette.RADIANT : ChartPalette.DIRE;
    List<ChartSpec.Band> bands =
        dips.stream()
            .map(
                d ->
                    new ChartSpec.Band(
                        Math.max(0, (d.fromMinute() - 1) * (double) SECONDS),
                        d.toSeconds(),
                        ChartPalette.DIP_BAND))
            .toList();
    return new ChartSpec.Panel(
        BehaviourWording.hero(heroes, slot) + ": gold per minute (last 3 minutes)",
        ChartSpec.ValueFormat.COMPACT,
        List.of(new ChartSpec.Series(seconds, gpm, colour, null, colour, null)),
        bands,
        hisPins);
  }

  private static List<ChartSpec.LegendEntry> legend(List<Found> found, List<Pin> all) {
    List<ChartSpec.LegendEntry> legend = new ArrayList<>();
    if (!found.isEmpty()) {
      legend.add(new ChartSpec.LegendEntry(ChartPalette.DIP, "Income fell"));
    }
    if (IncomePins.has(all, PinShape.CROSS)) {
      legend.add(IncomePins.legend(PinShape.CROSS, "Died"));
    }
    if (IncomePins.has(all, PinShape.DIAMOND)) {
      legend.add(IncomePins.legend(PinShape.DIAMOND, "Bought back"));
    }
    if (IncomePins.has(all, PinShape.TRIANGLE)) {
      legend.add(IncomePins.legend(PinShape.TRIANGLE, "Item bought"));
    }
    if (IncomePins.has(all, PinShape.STAR)) {
      legend.add(IncomePins.legend(PinShape.STAR, "Item paid for itself"));
    }
    return legend;
  }

  private static String verdict(List<Found> worst, Map<Long, String> heroes) {
    if (worst.isEmpty()) {
      return "No hero's income fell sharply.";
    }
    Found f = worst.getFirst();
    return "Biggest income drop: " + sentence(f, heroes) + ".";
  }

  private static String line(Found f, Map<Long, String> heroes) {
    return sentence(f, heroes) + ".";
  }

  private static String sentence(Found f, Map<Long, String> heroes) {
    Dip d = f.dip();
    String drop =
        String.format(
            Locale.ROOT,
            "%s's income fell from %s to %s gold per minute between %s and %s",
            BehaviourWording.hero(heroes, f.slot()),
            ChartFormat.compact(d.before()),
            ChartFormat.compact(d.lowest()),
            ChartFormat.clock(Math.max(0, d.causeFromSeconds())),
            ChartFormat.clock(d.toSeconds()));
    return f.why().isEmpty()
        ? drop + " (no stored reason)"
        : drop + ": " + String.join("; ", f.why());
  }

  private Map<Long, TreeMap<Integer, Long>> goldBySlot(long matchId) {
    Map<Long, TreeMap<Integer, Long>> gold = new HashMap<>();
    List<PlayerTimelineDomain> rows = timelineRepo.findByMatchId(matchId);
    if (rows == null) {
      return gold;
    }
    for (PlayerTimelineDomain row : rows) {
      if (row != null
          && row.getPlayerSlot() != null
          && row.getGameMinute() != null
          && row.getGold() != null
          && row.getGameMinute() >= 0
          && row.getGameMinute() <= LAST_MINUTE) {
        gold.computeIfAbsent(row.getPlayerSlot(), k -> new TreeMap<>())
            .put(row.getGameMinute(), row.getGold());
      }
    }
    return gold;
  }

  /** The tracked heroes with enough data, else the richest heroes; the richest first. */
  private static List<Long> chosen(
      Map<Long, TreeMap<Integer, Long>> gold, Set<Long> focus, int max) {
    Comparator<Long> richest =
        Comparator.comparingLong((Long s) -> gold.get(s).lastEntry().getValue()).reversed();
    List<Long> drawable =
        gold.keySet().stream().filter(s -> gold.get(s).size() > MIN_POINTS).toList();
    List<Long> tracked = drawable.stream().filter(focus::contains).sorted(richest).toList();
    return (tracked.isEmpty() ? drawable.stream().sorted(richest).toList() : tracked)
        .stream().limit(Math.max(1, max)).toList();
  }
}
