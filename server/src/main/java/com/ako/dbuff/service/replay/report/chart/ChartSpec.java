package com.ako.dbuff.service.replay.report.chart;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * What to draw: stacked panels sharing one game-time axis, with shaded stretches and labelled
 * vertical markers across them. The renderer decides the layout.
 *
 * @param endSeconds the game time at the right edge of the plot
 */
public record ChartSpec(
    String title,
    double endSeconds,
    List<Panel> panels,
    List<Band> bands,
    List<Marker> markers,
    List<LegendEntry> legend) {

  public ChartSpec {
    panels = List.copyOf(panels);
    bands = List.copyOf(bands);
    markers = List.copyOf(markers);
    legend = List.copyOf(legend);
  }

  /** A short digest of everything drawn, so a cache key can tell two different pictures apart. */
  public String fingerprint() {
    StringBuilder all = new StringBuilder(title).append('|').append(endSeconds);
    panels.forEach(
        p -> {
          all.append('|').append(p.label()).append(p.format()).append(p.bands()).append(p.pins());
          p.series()
              .forEach(
                  s ->
                      all.append(Arrays.hashCode(s.seconds()))
                          .append(',')
                          .append(Arrays.hashCode(s.values()))
                          .append(s.positive())
                          .append(s.positiveCaption())
                          .append(s.negative())
                          .append(s.negativeCaption()));
        });
    all.append(bands).append(markers).append(legend);
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(all.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * One plot; {@code label} is written in its corner. {@code bands} shade stretches of this panel
   * only and {@code pins} sit on its first series, so a panel can tell its own story.
   */
  public record Panel(
      String label, ValueFormat format, List<Series> series, List<Band> bands, List<Pin> pins) {
    public Panel {
      series = List.copyOf(series);
      bands = List.copyOf(bands);
      pins = List.copyOf(pins);
    }

    public Panel(String label, ValueFormat format, List<Series> series) {
      this(label, format, series, List.of(), List.of());
    }
  }

  /** The look of a pin: a cross, a diamond, a triangle or a star. */
  public enum PinShape {
    CROSS,
    DIAMOND,
    TRIANGLE,
    STAR
  }

  /**
   * A mark on a panel's first series at a game time; {@code label}, when not blank, is written near
   * the top of the panel with a thin line down to the mark.
   */
  public record Pin(double seconds, PinShape shape, Color color, String label) {}

  /**
   * Values at game seconds; NaN leaves a gap. The area between the line and zero is filled in
   * {@code positive} above zero and {@code negative} below it, each with its caption at the plot
   * edge.
   */
  public record Series(
      double[] seconds,
      double[] values,
      Color positive,
      String positiveCaption,
      Color negative,
      String negativeCaption) {}

  /** A stretch of game time shaded on every panel. */
  public record Band(double startSeconds, double endSeconds, Color color) {}

  /** A labelled vertical line at a game time. */
  public record Marker(double seconds, String label, Color color) {}

  /** A legend swatch; a null {@code shape} is a plain square. */
  public record LegendEntry(Color color, String text, PinShape shape) {
    public LegendEntry(Color color, String text) {
      this(color, text, null);
    }
  }

  /** How the numbers on a panel's value axis read. */
  public enum ValueFormat {
    /** {@code 850}, {@code 1.2k}, {@code -3k}: gold and experience. */
    COMPACT
  }
}
