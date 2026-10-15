package com.ako.dbuff.service.replay.report.chart;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChartRendererTest {
  private final ChartRenderer renderer = new ChartRenderer();

  private static BufferedImage decode(byte[] png) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  @Test
  void drawsAPngOfTheCappedSizeThatIsNotBlank() throws IOException {
    byte[] png = renderer.render(ChartTestSpecs.sample());

    BufferedImage image = decode(png);
    assertThat(image.getWidth()).isEqualTo(ChartRenderer.MAX_WIDTH);
    assertThat(image.getHeight()).isEqualTo(ChartRenderer.MAX_HEIGHT);
    Set<Integer> colours = new HashSet<>();
    for (int x = 0; x < image.getWidth(); x += 3) {
      for (int y = 0; y < image.getHeight(); y += 3) {
        colours.add(image.getRGB(x, y));
      }
    }
    assertThat(colours.size()).isGreaterThan(20);
    assertThat(png.length).isLessThan(500_000);
  }

  @Test
  void aChartWithNoPanelsStillDraws() throws IOException {
    assertThat(decode(renderer.render(ChartTestSpecs.empty())).getWidth())
        .isEqualTo(ChartRenderer.MAX_WIDTH);
  }

  @Test
  void aSeriesWithGapsAndNoValuesDoesNotBreakTheDrawing() throws IOException {
    ChartSpec spec =
        new ChartSpec(
            "Gaps",
            120,
            java.util.List.of(
                new ChartSpec.Panel(
                    "Gold lead",
                    ChartSpec.ValueFormat.COMPACT,
                    java.util.List.of(
                        new ChartSpec.Series(
                            new double[] {0, 60, 120},
                            new double[] {Double.NaN, 500, Double.NaN},
                            ChartPalette.RADIANT,
                            "Radiant ahead",
                            ChartPalette.DIRE,
                            "Dire ahead")))),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of());

    assertThat(decode(renderer.render(spec)).getHeight()).isEqualTo(ChartRenderer.MAX_HEIGHT);
  }

  @Test
  void aFailureIsReportedAsAChartRenderException() {
    assertThatThrownBy(() -> renderer.render(null)).isInstanceOf(ChartRenderException.class);
  }

  @Test
  void writesTheAxisFormats() {
    assertThat(ChartFormat.clock(0)).isEqualTo("0:00");
    assertThat(ChartFormat.clock(605)).isEqualTo("10:05");
    assertThat(ChartFormat.clock(3725)).isEqualTo("62:05");
    assertThat(ChartFormat.compact(850)).isEqualTo("850");
    assertThat(ChartFormat.compact(1200)).isEqualTo("1.2k");
    assertThat(ChartFormat.compact(-3000)).isEqualTo("-3k");
    assertThat(ChartFormat.compact(0)).isEqualTo("0");
    assertThat(ChartFormat.compact(999.5)).isEqualTo("1k");
    assertThat(ChartFormat.compact(999.4)).isEqualTo("999");
    assertThat(ChartFormat.compact(-999.5)).isEqualTo("-1k");
  }

  @Test
  void longAndCrowdedMarkerLabelsAreCutOrDroppedWithoutFailing() throws IOException {
    ChartSpec base = ChartTestSpecs.sample();
    java.util.List<ChartSpec.Marker> markers = new java.util.ArrayList<>();
    markers.add(new ChartSpec.Marker(100, "x".repeat(400), ChartPalette.DIRE));
    for (int i = 0; i < 12; i++) {
      markers.add(
          new ChartSpec.Marker(
              100 + i, "Radiant gained 3.1k from a won fight", ChartPalette.RADIANT));
    }
    ChartSpec spec =
        new ChartSpec(
            base.title(), base.endSeconds(), base.panels(), base.bands(), markers, base.legend());

    assertThat(decode(renderer.render(spec)).getWidth()).isEqualTo(ChartRenderer.MAX_WIDTH);
  }

  @Test
  void barsAndPanelMarkersDrawAndChangeTheFingerprint() throws IOException {
    java.util.function.Function<Double, ChartSpec> farm =
        gold ->
            new ChartSpec(
                "Farm",
                300,
                java.util.List.of(
                    new ChartSpec.Panel(
                        "Pudge",
                        ChartSpec.ValueFormat.COMPACT,
                        java.util.List.of(),
                        java.util.List.of(
                            new ChartSpec.BarSeries(
                                new double[] {0, 60, 120, Double.NaN},
                                new double[] {gold, Double.NaN, -5, 80},
                                60,
                                ChartPalette.RADIANT)),
                        java.util.List.of(
                            new ChartSpec.Marker(
                                90, "Battle Fury (1:30), paid back at 5:00", ChartPalette.DIRE)))),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());

    byte[] png = renderer.render(farm.apply(120.0));

    assertThat(decode(png).getHeight()).isEqualTo(ChartRenderer.MAX_HEIGHT);
    assertThat(png.length).isGreaterThan(2000);
    assertThat(farm.apply(120.0).fingerprint()).isNotEqualTo(farm.apply(121.0).fingerprint());
  }

  private static ChartSpec.Panel barPanel(String label, int labels) {
    java.util.List<ChartSpec.Marker> markers = new java.util.ArrayList<>();
    for (int i = 0; i < labels; i++) {
      markers.add(
          new ChartSpec.Marker(
              100 + i * 20,
              "Battle Fury and a very long item name (14:40), paid back at 31:40",
              ChartPalette.DIRE));
    }
    return new ChartSpec.Panel(
        label,
        ChartSpec.ValueFormat.COMPACT,
        java.util.List.of(),
        java.util.List.of(
            new ChartSpec.BarSeries(
                new double[] {0, 60, 120}, new double[] {300, 400, 500}, 60, ChartPalette.RADIANT)),
        markers);
  }

  @Test
  void fiveCrowdedPanelsKeepTheirPlotsAndTheirMarkerLines() throws IOException {
    java.util.List<ChartSpec.Panel> panels = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      panels.add(barPanel("Hero " + i, 4));
    }
    ChartSpec spec =
        new ChartSpec(
            "Crowded", 600, panels, java.util.List.of(), java.util.List.of(), java.util.List.of());

    BufferedImage image = decode(renderer.render(spec));

    int markerLine = ChartPalette.DIRE.getRGB();
    int barPixels = 0;
    int lineColumns = 0;
    for (int x = 64; x < 980; x++) {
      boolean line = false;
      for (int y = 40; y < 470; y++) {
        int rgb = image.getRGB(x, y);
        line |= rgb == markerLine;
        if (rgb != 0xFFFFFFFF && (rgb & 0xFF) < 0x80 && ((rgb >> 8) & 0xFF) > 0x90) {
          barPixels++;
        }
      }
      lineColumns += line ? 1 : 0;
    }
    assertThat(barPixels).as("bars are still drawn in every panel").isGreaterThan(500);
    assertThat(lineColumns).as("marker lines are kept even without labels").isGreaterThan(0);
  }

  @Test
  void globalAndPanelMarkersLineUpOnTheSameTimeAxis() throws IOException {
    ChartSpec.Panel panel = barPanel("Hero", 0);
    ChartSpec.Panel withOwn =
        new ChartSpec.Panel(
            panel.label(),
            panel.format(),
            panel.series(),
            panel.bars(),
            java.util.List.of(new ChartSpec.Marker(300, "Own", new java.awt.Color(0x0000FF))));
    ChartSpec spec =
        new ChartSpec(
            "Mixed",
            600,
            java.util.List.of(withOwn, panel),
            java.util.List.of(),
            java.util.List.of(new ChartSpec.Marker(300, "Global", new java.awt.Color(0xFF00FF))),
            java.util.List.of());

    BufferedImage image = decode(renderer.render(spec));

    int x = 64 + (int) Math.round(300 / 600.0 * (1000 - 20 - 64));
    assertThat(columnHas(image, x, 0xFF00FF, 300, 470))
        .as("global line in the lower panel")
        .isTrue();
    assertThat(columnHas(image, x, 0x0000FF, 60, 250)).as("own line in the first panel").isTrue();
    assertThat(columnHas(image, x, 0x0000FF, 300, 470)).as("own line stays in its panel").isFalse();
  }

  private static boolean columnHas(BufferedImage image, int x, int rgb, int fromY, int toY) {
    for (int y = fromY; y < toY; y++) {
      for (int dx = -1; dx <= 1; dx++) {
        if ((image.getRGB(x + dx, y) & 0xFFFFFF) == rgb) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void theFingerprintSeesEveryDigitOfEveryNumber() {
    java.util.function.DoubleFunction<ChartSpec> spec =
        v ->
            new ChartSpec(
                "T",
                600,
                java.util.List.of(
                    new ChartSpec.Panel(
                        "P",
                        ChartSpec.ValueFormat.COMPACT,
                        java.util.List.of(),
                        java.util.List.of(
                            new ChartSpec.BarSeries(
                                new double[] {0}, new double[] {v}, 60, ChartPalette.RADIANT)),
                        java.util.List.of())),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());

    assertThat(spec.apply(1.0).fingerprint()).hasSize(64).isEqualTo(spec.apply(1.0).fingerprint());
    assertThat(spec.apply(1.0).fingerprint()).isNotEqualTo(spec.apply(1.0000000001).fingerprint());
  }
}
