package com.ako.dbuff.service.replay.report.chart;

import java.util.List;
import java.util.stream.IntStream;

final class ChartTestSpecs {
  private ChartTestSpecs() {}

  static ChartSpec sample() {
    double[] seconds = IntStream.range(0, 41).mapToDouble(m -> m * 60.0).toArray();
    double[] gold =
        IntStream.range(0, 41).mapToDouble(m -> Math.sin(m / 6.0) * 4000 + m * 60).toArray();
    double[] xp =
        IntStream.range(0, 41).mapToDouble(m -> Math.cos(m / 8.0) * 3000 - m * 40).toArray();
    return new ChartSpec(
        "Team advantage",
        2400,
        List.of(panel("Gold lead", seconds, gold), panel("Experience lead", seconds, xp)),
        List.of(new ChartSpec.Band(600, 660, ChartPalette.FIGHT)),
        List.of(
            new ChartSpec.Marker(700, "Radiant gained 3.1k from a won fight", ChartPalette.RADIANT),
            new ChartSpec.Marker(760, "Dire gained 2k from buildings", ChartPalette.DIRE)),
        List.of(new ChartSpec.LegendEntry(ChartPalette.FIGHT_LEGEND, "Important fight")));
  }

  private static ChartSpec.Panel panel(String label, double[] seconds, double[] values) {
    return new ChartSpec.Panel(
        label,
        ChartSpec.ValueFormat.COMPACT,
        List.of(
            new ChartSpec.Series(
                seconds,
                values,
                ChartPalette.RADIANT,
                "Radiant ahead",
                ChartPalette.DIRE,
                "Dire ahead")));
  }

  static ChartSpec empty() {
    return new ChartSpec("Empty", 60, List.of(), List.of(), List.of(), List.of());
  }
}
