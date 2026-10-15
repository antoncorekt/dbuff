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
}
