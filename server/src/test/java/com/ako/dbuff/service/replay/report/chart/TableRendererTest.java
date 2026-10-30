package com.ako.dbuff.service.replay.report.chart;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableRendererTest {
  private final TableRenderer renderer = new TableRenderer();

  private static byte[] icon() throws IOException {
    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    for (int x = 0; x < 32; x++) {
      for (int y = 0; y < 32; y++) {
        image.setRGB(x, y, 0xFF3366CC);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private static TableSpec table(String name, byte[] icon, int rows) {
    List<List<TableSpec.Cell>> body = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      body.add(
          List.of(
              new TableSpec.Cell(icon, name, null, ChartPalette.RADIANT),
              new TableSpec.Cell(icon, "axe_counter_helix", "#14", null)));
    }
    return new TableSpec("Draft", List.of("Player", "Pick"), body);
  }

  private static BufferedImage decode(byte[] png) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  @Test
  void drawsADecodablePngWithinTheLimitsThatIsNotBlank() throws IOException {
    BufferedImage image = decode(renderer.render(table("Phoenix", icon(), 4)).orElseThrow());

    assertThat(image.getWidth()).isBetween(1, TableRenderer.MAX_WIDTH);
    assertThat(image.getHeight()).isBetween(1, TableRenderer.MAX_HEIGHT);
    Set<Integer> colours = new HashSet<>();
    for (int x = 0; x < image.getWidth(); x += 2) {
      for (int y = 0; y < image.getHeight(); y += 2) {
        colours.add(image.getRGB(x, y));
      }
    }
    assertThat(colours.size()).isGreaterThan(10);
  }

  @Test
  void aCyrillicNameDoesNotThrow() {
    assertThat(renderer.render(table("Пастух лолей", icon0(), 2))).isPresent();
  }

  @Test
  void heightGrowsWithTheRows() throws IOException {
    int few = decode(renderer.render(table("A", null, 2)).orElseThrow()).getHeight();
    int many = decode(renderer.render(table("A", null, 10)).orElseThrow()).getHeight();

    assertThat(many).isGreaterThan(few);
  }

  @Test
  void aVeryLongNameIsCutToTheMaximumWidth() throws IOException {
    BufferedImage image = decode(renderer.render(table("W".repeat(300), null, 2)).orElseThrow());

    assertThat(image.getWidth()).isLessThanOrEqualTo(TableRenderer.MAX_WIDTH);
  }

  @Test
  void anEmptyTableIsRefused() {
    assertThat(renderer.render(new TableSpec("Draft", List.of("Player"), List.of()))).isEmpty();
    assertThat(renderer.render(new TableSpec("Draft", List.of(), List.of(List.of())))).isEmpty();
  }

  @Test
  void moreRowsThanFitAreRefused() {
    assertThat(renderer.render(table("A", null, TableRenderer.MAX_ROWS + 1))).isEmpty();
    assertThat(renderer.render(table("A", null, TableRenderer.MAX_ROWS))).isPresent();
  }

  @Test
  void aMissingOrBrokenIconDrawsTextOnly() {
    List<List<TableSpec.Cell>> rows =
        List.of(List.of(new TableSpec.Cell(new byte[] {1, 2, 3}, "Axe", null, null)));

    assertThat(renderer.render(new TableSpec("T", List.of("Pick"), rows))).isPresent();
    assertThat(renderer.render(table("A", null, 1))).isPresent();
  }

  @Test
  void theFingerprintCoversEverythingShown() throws IOException {
    TableSpec base = table("A", icon(), 2);

    assertThat(base.fingerprint()).isEqualTo(table("A", icon(), 2).fingerprint());
    assertThat(base.fingerprint()).isNotEqualTo(table("B", icon(), 2).fingerprint());
    assertThat(base.fingerprint()).isNotEqualTo(table("A", null, 2).fingerprint());
    assertThat(base.fingerprint()).isNotEqualTo(table("A", icon(), 3).fingerprint());
    TableSpec dire =
        new TableSpec(
            "Draft",
            List.of("Player", "Pick"),
            List.of(
                List.of(new TableSpec.Cell(icon(), "A", null, Color.RED)),
                List.of(TableSpec.Cell.text("x"))));
    assertThat(dire.fingerprint()).isNotEqualTo(base.fingerprint());
  }

  private static byte[] icon0() {
    try {
      return icon();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
