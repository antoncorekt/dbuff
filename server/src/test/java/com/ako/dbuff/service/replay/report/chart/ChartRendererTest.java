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
  void panelBandsPinsAndLegendShapesDrawEvenOutsideTheSeries() throws IOException {
    ChartSpec.Series series =
        new ChartSpec.Series(
            new double[] {180, 240, 300},
            new double[] {500, Double.NaN, 700},
            ChartPalette.RADIANT,
            null,
            ChartPalette.RADIANT,
            null);
    java.util.List<ChartSpec.Pin> pins = new java.util.ArrayList<>();
    for (ChartSpec.PinShape shape : ChartSpec.PinShape.values()) {
      pins.add(new ChartSpec.Pin(0, shape, ChartPalette.DEATH, "A label that is long " + shape));
      pins.add(new ChartSpec.Pin(250, shape, ChartPalette.PURCHASE, "Item " + shape));
      pins.add(new ChartSpec.Pin(9999, shape, ChartPalette.PAYBACK, null));
    }
    pins.add(
        new ChartSpec.Pin(100, ChartSpec.PinShape.CROSS, ChartPalette.DEATH, "x".repeat(2000)));
    ChartSpec spec =
        new ChartSpec(
            "Pins",
            300,
            java.util.List.of(
                new ChartSpec.Panel(
                    "Hero",
                    ChartSpec.ValueFormat.COMPACT,
                    java.util.List.of(series),
                    java.util.List.of(new ChartSpec.Band(200, 260, ChartPalette.DIP_BAND)),
                    pins),
                new ChartSpec.Panel(
                    "No data",
                    ChartSpec.ValueFormat.COMPACT,
                    java.util.List.of(),
                    java.util.List.of(),
                    pins)),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(
                new ChartSpec.LegendEntry(ChartPalette.DEATH, "Died", ChartSpec.PinShape.CROSS)));

    assertThat(decode(renderer.render(spec)).getHeight()).isEqualTo(ChartRenderer.MAX_HEIGHT);
  }

  @Test
  void pinsAndPanelBandsChangeTheFingerprint() {
    ChartSpec.Series series =
        new ChartSpec.Series(
            new double[] {0, 60},
            new double[] {1, 2},
            ChartPalette.RADIANT,
            "a",
            ChartPalette.DIRE,
            "b");
    java.util.function.Function<java.util.List<ChartSpec.Pin>, String> print =
        pins ->
            new ChartSpec(
                    "t",
                    60,
                    java.util.List.of(
                        new ChartSpec.Panel(
                            "p",
                            ChartSpec.ValueFormat.COMPACT,
                            java.util.List.of(series),
                            java.util.List.of(),
                            pins)),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of())
                .fingerprint();

    assertThat(print.apply(java.util.List.of()))
        .isNotEqualTo(
            print.apply(
                java.util.List.of(
                    new ChartSpec.Pin(30, ChartSpec.PinShape.CROSS, ChartPalette.DEATH, null))));
  }
}
