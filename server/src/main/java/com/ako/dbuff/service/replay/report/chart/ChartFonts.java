package com.ako.dbuff.service.replay.report.chart;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * The chart font is bundled (DejaVu Sans, free licence in the same folder) so a server image with
 * no system fonts still draws readable text. If it cannot be read, the JVM's own sans-serif is
 * used.
 */
@Slf4j
final class ChartFonts {
  private static final String RESOURCE = "/chart/DejaVuSans.ttf";
  private static final Font BASE = load();

  private ChartFonts() {}

  static Font plain(float size) {
    return BASE.deriveFont(Font.PLAIN, size);
  }

  static Font bold(float size) {
    return BASE.deriveFont(Font.BOLD, size);
  }

  private static Font load() {
    try (InputStream in = ChartFonts.class.getResourceAsStream(RESOURCE)) {
      if (in != null) {
        return Font.createFont(Font.TRUETYPE_FONT, in);
      }
      log.warn("Chart font {} is missing, using the system sans-serif", RESOURCE);
    } catch (IOException | FontFormatException | RuntimeException e) {
      log.warn("Chart font {} could not be loaded, using the system sans-serif", RESOURCE, e);
    }
    return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
  }
}
