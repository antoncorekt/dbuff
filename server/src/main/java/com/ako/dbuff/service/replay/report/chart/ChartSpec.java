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
          all.append('|').append(p.label()).append(p.format());
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

  /** One plot; {@code label} is written in its corner. */
  public record Panel(String label, ValueFormat format, List<Series> series) {
    public Panel {
      series = List.copyOf(series);
    }
  }

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

  public record LegendEntry(Color color, String text) {}

  /** How the numbers on a panel's value axis read. */
  public enum ValueFormat {
    /** {@code 850}, {@code 1.2k}, {@code -3k}: gold and experience. */
    COMPACT
  }
}
