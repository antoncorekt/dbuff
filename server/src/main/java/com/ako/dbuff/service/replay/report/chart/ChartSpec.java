package com.ako.dbuff.service.replay.report.chart;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

  /**
   * A digest of everything drawn (SHA-256 over a canonical encoding, every number written in full),
   * so a cache key can tell two different pictures apart.
   */
  public String fingerprint() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      text(out, title);
      out.writeDouble(endSeconds);
      out.writeInt(panels.size());
      for (Panel p : panels) {
        text(out, p.label());
        text(out, p.format().name());
        out.writeInt(p.series().size());
        for (Series s : p.series()) {
          doubles(out, s.seconds());
          doubles(out, s.values());
          out.writeInt(s.positive().getRGB());
          text(out, s.positiveCaption());
          out.writeInt(s.negative().getRGB());
          text(out, s.negativeCaption());
        }
        out.writeInt(p.bars().size());
        for (BarSeries b : p.bars()) {
          doubles(out, b.seconds());
          doubles(out, b.values());
          out.writeDouble(b.widthSeconds());
          out.writeInt(b.color().getRGB());
        }
        markers(out, p.markers());
        bands(out, p.bands());
        out.writeInt(p.pins().size());
        for (Pin pin : p.pins()) {
          out.writeDouble(pin.seconds());
          text(out, pin.shape().name());
          out.writeInt(pin.color().getRGB());
          text(out, pin.label());
        }
      }
      bands(out, bands);
      markers(out, markers);
      out.writeInt(legend.size());
      for (LegendEntry l : legend) {
        out.writeInt(l.color().getRGB());
        text(out, l.text());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void text(DataOutputStream out, String value) throws IOException {
    out.writeBoolean(value != null);
    if (value != null) {
      byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
      out.writeInt(utf8.length);
      out.write(utf8);
    }
  }

  private static void doubles(DataOutputStream out, double[] values) throws IOException {
    out.writeInt(values.length);
    for (double v : values) {
      out.writeDouble(v);
    }
  }

  private static void bands(DataOutputStream out, List<Band> bands) throws IOException {
    out.writeInt(bands.size());
    for (Band b : bands) {
      out.writeDouble(b.startSeconds());
      out.writeDouble(b.endSeconds());
      out.writeInt(b.color().getRGB());
    }
  }

  private static void markers(DataOutputStream out, List<Marker> markers) throws IOException {
    out.writeInt(markers.size());
    for (Marker m : markers) {
      out.writeDouble(m.seconds());
      text(out, m.label());
      out.writeInt(m.color().getRGB());
    }
  }

  /**
   * One plot; {@code label} is written in its corner. {@code bands} shade stretches of this panel
   * only and {@code pins} sit on its first series, so a panel can tell its own story. The {@code
   * bars} are stacked from zero in list order; the {@code markers} are labelled lines of this panel
   * alone, with their labels above its plot.
   */
  public record Panel(
      String label,
      ValueFormat format,
      List<Series> series,
      List<Band> bands,
      List<Pin> pins,
      List<BarSeries> bars,
      List<Marker> markers) {
    public Panel {
      series = List.copyOf(series);
      bands = List.copyOf(bands);
      pins = List.copyOf(pins);
      bars = List.copyOf(bars);
      markers = List.copyOf(markers);
    }

    public Panel(
        String label, ValueFormat format, List<Series> series, List<Band> bands, List<Pin> pins) {
      this(label, format, series, bands, pins, List.of(), List.of());
    }

    public Panel(String label, ValueFormat format, List<Series> series) {
      this(label, format, series, List.of(), List.of());
    }
  }

  /**
   * One layer of stacked bars: {@code values[i]} fills {@code [seconds[i], seconds[i] +
   * widthSeconds)}; NaN and non-positive values draw nothing.
   */
  public record BarSeries(double[] seconds, double[] values, double widthSeconds, Color color) {}

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
