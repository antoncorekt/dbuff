package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeries;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeriesReader;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.spike.SpikeCause;
import com.ako.dbuff.service.replay.report.chart.ChartFormat;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The gold and experience lead of the tracked team (Radiant when the channel tracks no single team)
 * over the game, from the stored timelines, with the important fights shaded and the biggest swings
 * labelled in words.
 */
@Component
@RequiredArgsConstructor
public class AdvantageChartBuilder {
  private final AdvantageSeriesReader seriesReader;
  private final GameSpikeRepo spikeRepo;
  private final FightExplanationReader explanationReader;

  /** The chart and what it says in one line. */
  public record AdvantageChart(ChartSpec spec, String verdict) {}

  /** The side drawn as "up": the tracked team, else Radiant. */
  public static TeamSide viewpoint(TeamSide yours) {
    return yours == null ? TeamSide.RADIANT : yours;
  }

  /** Empty when the match has no stored timelines to draw. */
  public Optional<AdvantageChart> build(long matchId, TeamSide yours, int maxSwings) {
    Optional<AdvantageSeries> read = seriesReader.read(matchId);
    if (read.isEmpty()) {
      return Optional.empty();
    }
    AdvantageSeries series = read.get();
    TeamSide up = viewpoint(yours);
    int points = Math.min(series.radiantGold().length, series.radiantXp().length);
    if (points == 0) {
      return Optional.empty();
    }
    double[] seconds = new double[points];
    double[] gold = new double[points];
    double[] xp = new double[points];
    for (int i = 0; i < points; i++) {
      seconds[i] = (double) i * AdvantageSeries.MINUTE;
      gold[i] = series.gold(up, seconds[i]);
      xp[i] = series.xp(up, seconds[i]);
    }
    double end = Math.max(seconds[points - 1], AdvantageSeries.MINUTE);
    List<ChartSpec.Band> fights = fightBands(matchId);
    List<ChartSpec.Marker> swings = swingMarkers(matchId, yours, maxSwings);
    List<ChartSpec.LegendEntry> legend = new ArrayList<>();
    if (!fights.isEmpty()) {
      legend.add(new ChartSpec.LegendEntry(ChartPalette.FIGHT_LEGEND, "Important fight"));
    }
    if (!swings.isEmpty()) {
      legend.add(
          new ChartSpec.LegendEntry(
              Color.GRAY, "Big swing: who gained how much net worth, and why"));
    }
    ChartSpec spec =
        new ChartSpec(
            "Team advantage",
            end,
            List.of(
                panel("Gold lead", seconds, gold, up, yours),
                panel("Experience lead", seconds, xp, up, yours)),
            fights,
            swings,
            legend);
    return Optional.of(new AdvantageChart(spec, verdict(seconds, gold, up, yours)));
  }

  private static ChartSpec.Panel panel(
      String label, double[] seconds, double[] values, TeamSide up, TeamSide yours) {
    TeamSide down = up.opponent();
    return new ChartSpec.Panel(
        label,
        ChartSpec.ValueFormat.COMPACT,
        List.of(
            new ChartSpec.Series(
                seconds,
                values,
                colour(up),
                Prettify.teamOf(up.name(), yours) + " ahead",
                colour(down),
                Prettify.teamOf(down.name(), yours) + " ahead")));
  }

  private static Color colour(TeamSide side) {
    return side == TeamSide.RADIANT ? ChartPalette.RADIANT : ChartPalette.DIRE;
  }

  private List<ChartSpec.Band> fightBands(long matchId) {
    return explanationReader.importantFights(matchId).stream()
        .map(LostFight::fight)
        .filter(f -> f.getStartSeconds() != null && f.getEndSeconds() != null)
        .map(f -> new ChartSpec.Band(f.getStartSeconds(), f.getEndSeconds(), ChartPalette.FIGHT))
        .toList();
  }

  private List<ChartSpec.Marker> swingMarkers(long matchId, TeamSide yours, int maxSwings) {
    return spikeRepo.findByMatchId(matchId).stream()
        .filter(s -> !SpikeCause.OTHER.name().equals(s.getPrimaryCause()))
        .filter(AdvantageChartBuilder::drawable)
        .sorted(Comparator.comparingLong(GameSpikeDomain::getDeltaGold).reversed())
        .limit(maxSwings)
        .map(s -> marker(s, yours))
        .toList();
  }

  private static boolean drawable(GameSpikeDomain spike) {
    return spike.getStartSeconds() != null
        && spike.getEndSeconds() != null
        && spike.getDeltaGold() != null
        && Enums.find(TeamSide.class, spike.getBenefitingTeam()) != null;
  }

  private static ChartSpec.Marker marker(GameSpikeDomain spike, TeamSide yours) {
    TeamSide winner = Enums.find(TeamSide.class, spike.getBenefitingTeam());
    String cause = SwingCauses.phrase(spike.getPrimaryCause());
    String label =
        String.format(
            Locale.ROOT,
            "Swung %s gold toward %s%s",
            ChartFormat.compact(spike.getDeltaGold()),
            Prettify.teamOf(spike.getBenefitingTeam(), yours),
            cause.isEmpty() ? "" : " from " + cause);
    return new ChartSpec.Marker(
        (spike.getStartSeconds() + spike.getEndSeconds()) / 2.0, label, colour(winner).darker());
  }

  private static String verdict(double[] seconds, double[] gold, TeamSide up, TeamSide yours) {
    int best = indexOf(gold, true);
    int worst = indexOf(gold, false);
    boolean upLed = best >= 0 && gold[best] > 0;
    boolean downLed = worst >= 0 && gold[worst] < 0;
    if (!upLed && !downLed) {
      return "Neither team led in gold.";
    }
    TeamSide down = up.opponent();
    String lead =
        upLed
            ? String.format(
                Locale.ROOT,
                "%s was ahead by up to %s gold (at %s)",
                Prettify.teamOf(up.name(), yours),
                ChartFormat.compact(gold[best]),
                ChartFormat.clock(seconds[best]))
            : Prettify.teamOf(up.name(), yours) + " never led in gold";
    String other =
        downLed
            ? String.format(
                Locale.ROOT,
                "%s by up to %s (at %s)",
                Prettify.teamOf(down.name(), yours),
                ChartFormat.compact(-gold[worst]),
                ChartFormat.clock(seconds[worst]))
            : Prettify.teamOf(down.name(), yours) + " never led";
    return lead + "; " + other + ".";
  }

  private static int indexOf(double[] values, boolean max) {
    int found = -1;
    for (int i = 0; i < values.length; i++) {
      if (!Double.isNaN(values[i])
          && (found < 0 || (max ? values[i] > values[found] : values[i] < values[found]))) {
        found = i;
      }
    }
    return found;
  }
}
