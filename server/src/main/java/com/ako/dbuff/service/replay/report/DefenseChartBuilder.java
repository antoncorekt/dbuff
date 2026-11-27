package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.MissingDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.chart.ChartFormat;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.Pin;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.PinShape;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Both teams' summed health and effective health over the game as four panels, with the defensive
 * items they got and what each team lacked, all read from what the replay analysis stored.
 */
@Component
@RequiredArgsConstructor
public class DefenseChartBuilder {
  static final String END = "END";
  private static final int SECONDS = 60;
  private static final int[] MARKER_MINUTES = {10, 20, 30};
  private static final Color MARKER = new Color(0x6E7781);

  private final TeamDefensePointRepo pointRepo;
  private final TeamDefenseItemRepo itemRepo;
  private final MissingDefenseItemRepo missingRepo;
  private final DefenseReportProperties properties;

  /** The chart and what it says: the verdict, then one line per checkpoint and missing item. */
  public record DefenseChart(ChartSpec spec, String verdict, List<String> bullets) {}

  /** Empty when the match has no stored defence points. */
  public Optional<DefenseChart> build(long matchId) {
    List<TeamDefensePointDomain> all = orEmpty(pointRepo.findByMatchId(matchId));
    List<TeamDefensePointDomain> points =
        all.stream()
            .filter(p -> p != null && p.getMinute() != null && p.getTeam() != null)
            .toList();
    if (points.isEmpty()) {
      return Optional.empty();
    }
    List<TeamDefenseItemDomain> items =
        orEmpty(itemRepo.findByMatchId(matchId)).stream()
            .filter(i -> i != null && i.getAtSecond() != null && i.getItem() != null)
            .toList();
    List<MissingDefenseItemDomain> missing = orEmpty(missingRepo.findByMatchId(matchId));
    int lastMinute = points.stream().mapToInt(TeamDefensePointDomain::getMinute).max().orElse(0);
    List<Pin> pins = pins(items);
    List<ChartSpec.Panel> panels = new ArrayList<>();
    panels.add(panel("Max HP", points, TeamDefensePointDomain::getMaxHpSum, List.of()));
    panels.add(
        panel("Physical effective HP", points, TeamDefensePointDomain::getEhpPhys, List.of()));
    panels.add(panel("Magical effective HP", points, TeamDefensePointDomain::getEhpMag, List.of()));
    panels.add(panel("Mixed effective HP", points, TeamDefensePointDomain::getEhpMix, pins));
    List<ChartSpec.Marker> markers = new ArrayList<>();
    for (int minute : MARKER_MINUTES) {
      if (minute <= lastMinute) {
        markers.add(new ChartSpec.Marker(minute * (double) SECONDS, minute + ":00", MARKER));
      }
    }
    List<ChartSpec.LegendEntry> legend = new ArrayList<>();
    legend.add(new ChartSpec.LegendEntry(ChartPalette.RADIANT, "Radiant"));
    legend.add(new ChartSpec.LegendEntry(ChartPalette.DIRE, "Dire"));
    if (!pins.isEmpty()) {
      legend.add(
          new ChartSpec.LegendEntry(ChartPalette.PURCHASE, "Defensive item", PinShape.TRIANGLE));
    }
    ChartSpec spec =
        new ChartSpec(
            "Team defense", lastMinute * (double) SECONDS, panels, List.of(), markers, legend);
    Map<String, Map<String, TeamDefensePointDomain>> checkpoints = checkpoints(points);
    List<String> bullets = new ArrayList<>();
    checkpoints.forEach((name, teams) -> bullets.add(bullet(name, teams)));
    missing.stream()
        .filter(m -> m.getEvidence() != null && !m.getEvidence().isBlank())
        .sorted(
            Comparator.comparing(
                MissingDefenseItemDomain::getMinute, Comparator.nullsLast(Integer::compare)))
        .forEach(m -> bullets.add(m.getEvidence()));
    return Optional.of(new DefenseChart(spec, verdict(checkpoints), bullets));
  }

  private ChartSpec.Panel panel(
      String label,
      List<TeamDefensePointDomain> points,
      Function<TeamDefensePointDomain, Integer> value,
      List<Pin> pins) {
    List<ChartSpec.Series> series = new ArrayList<>();
    for (TeamSide side : List.of(TeamSide.RADIANT, TeamSide.DIRE)) {
      List<TeamDefensePointDomain> kept =
          points.stream()
              .filter(p -> side.name().equals(p.getTeam()) && !END.equals(p.getCheckpoint()))
              .filter(p -> p.getMinute() % properties.getStepMinutes() == 0)
              .filter(p -> value.apply(p) != null)
              .sorted(Comparator.comparingInt(TeamDefensePointDomain::getMinute))
              .toList();
      double[] seconds = kept.stream().mapToDouble(p -> p.getMinute() * (double) SECONDS).toArray();
      double[] values = kept.stream().mapToDouble(p -> value.apply(p)).toArray();
      Color colour = side == TeamSide.RADIANT ? ChartPalette.RADIANT : ChartPalette.DIRE;
      series.add(new ChartSpec.Series(seconds, values, colour, null, colour, null));
    }
    return new ChartSpec.Panel(label, ChartSpec.ValueFormat.COMPACT, series, List.of(), pins);
  }

  private static List<Pin> pins(List<TeamDefenseItemDomain> items) {
    return items.stream()
        .sorted(Comparator.comparingInt(TeamDefenseItemDomain::getAtSecond))
        .map(
            i ->
                new Pin(
                    i.getAtSecond(),
                    PinShape.TRIANGLE,
                    "DIRE".equals(i.getTeam()) ? ChartPalette.DIRE : ChartPalette.RADIANT,
                    Prettify.ability("item_" + i.getItem())))
        .toList();
  }

  /** M10, M20, M30 and END in game order, each with the rows of the teams that have one. */
  private static Map<String, Map<String, TeamDefensePointDomain>> checkpoints(
      List<TeamDefensePointDomain> points) {
    Map<String, Map<String, TeamDefensePointDomain>> byName = new LinkedHashMap<>();
    for (String name : List.of("M10", "M20", "M30", END)) {
      Map<String, TeamDefensePointDomain> teams = new LinkedHashMap<>();
      for (TeamDefensePointDomain p : points) {
        if (name.equals(p.getCheckpoint())) {
          teams.put(p.getTeam(), p);
        }
      }
      if (!teams.isEmpty()) {
        byName.put(name, teams);
      }
    }
    return byName;
  }

  private static String bullet(String checkpoint, Map<String, TeamDefensePointDomain> teams) {
    String head = END.equals(checkpoint) ? "At the end" : "At " + minutes(checkpoint) + ":00";
    List<String> parts = new ArrayList<>();
    for (String team : List.of("RADIANT", "DIRE")) {
      TeamDefensePointDomain p = teams.get(team);
      if (p != null) {
        parts.add(Prettify.team(team) + " " + describe(p));
      }
    }
    return head + ": " + String.join("; ", parts) + ".";
  }

  private static String describe(TeamDefensePointDomain p) {
    return String.format(
        Locale.ROOT,
        "%s HP, armor %s, resistance %s, effective HP %s",
        number(p.getMaxHpSum(), 1),
        number(p.getArmorAvgX10(), 10),
        percent(p.getMagicResistAvgX10()),
        number(p.getEhpMix(), 1));
  }

  private static String number(Integer value, int scale) {
    if (value == null) {
      return "?";
    }
    return scale == 1
        ? ChartFormat.compact(value)
        : String.format(Locale.ROOT, "%.1f", value / (double) scale);
  }

  private static String percent(Integer x10) {
    return x10 == null ? "?" : Math.round(x10 / 10.0) + "%";
  }

  private static int minutes(String checkpoint) {
    return Integer.parseInt(checkpoint.substring(1));
  }

  /** The reached checkpoint (not the end) where one team's mixed effective HP led the most. */
  private static String verdict(Map<String, Map<String, TeamDefensePointDomain>> checkpoints) {
    String best = null;
    double bestGap = 0;
    String leader = null;
    for (Map.Entry<String, Map<String, TeamDefensePointDomain>> e : checkpoints.entrySet()) {
      if (END.equals(e.getKey())) {
        continue;
      }
      TeamDefensePointDomain radiant = e.getValue().get("RADIANT");
      TeamDefensePointDomain dire = e.getValue().get("DIRE");
      if (radiant == null
          || dire == null
          || radiant.getEhpMix() == null
          || dire.getEhpMix() == null) {
        continue;
      }
      int low = Math.min(radiant.getEhpMix(), dire.getEhpMix());
      if (low <= 0) {
        continue;
      }
      double gap = Math.abs(radiant.getEhpMix() - dire.getEhpMix()) / (double) low;
      if (gap > bestGap) {
        bestGap = gap;
        best = e.getKey();
        leader = radiant.getEhpMix() > dire.getEhpMix() ? "RADIANT" : "DIRE";
      }
    }
    if (best == null) {
      return "Neither team had clearly more effective HP at a checkpoint";
    }
    String other = "RADIANT".equals(leader) ? "DIRE" : "RADIANT";
    return String.format(
        Locale.ROOT,
        "At %d:00 %s had %d%% more effective HP against the damage %s dealt",
        minutes(best),
        Prettify.team(leader),
        Math.round(bestGap * 100),
        Prettify.team(other));
  }

  private static <T> List<T> orEmpty(List<T> rows) {
    return rows == null ? List.of() : rows;
  }
}
