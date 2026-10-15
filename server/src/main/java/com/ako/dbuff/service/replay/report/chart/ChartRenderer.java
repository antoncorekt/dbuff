package com.ako.dbuff.service.replay.report.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Draws a {@link ChartSpec} with Java2D into PNG bytes, off screen (the JVM runs headless). The
 * image never exceeds {@link #MAX_WIDTH} by {@link #MAX_HEIGHT}.
 */
@Component
public class ChartRenderer {
  public static final int MAX_WIDTH = 1000;
  public static final int MAX_HEIGHT = 500;

  private static final Color BACKGROUND = new Color(0xFFFFFF);
  private static final Color TEXT = new Color(0x1F2328);
  private static final Color MUTED = new Color(0x59636E);
  private static final Color GRID = new Color(0xE1E4E8);
  private static final Color ZERO_LINE = new Color(0x57606A);
  private static final float TITLE_SIZE = 18;
  private static final float LABEL_SIZE = 13;
  private static final int LEFT = 64;
  private static final int RIGHT = 20;
  private static final int TOP = 40;
  private static final int AXIS_HEIGHT = 24;
  private static final int LEGEND_HEIGHT = 28;
  private static final int PANEL_GAP = 22;
  private static final int LABEL_LANE = 20;
  private static final int MAX_LANES = 4;
  private static final int TICK_PIXELS = 36;
  private static final int PIN_RADIUS = 5;
  private static final int PIN_LANES = 3;
  private static final int PIN_LANE_HEIGHT = 15;
  private static final double MIN_MARGIN = 0.12;

  static {
    System.setProperty("java.awt.headless", "true");
  }

  /** The PNG; throws {@link ChartRenderException} when drawing or encoding fails. */
  public byte[] render(ChartSpec spec) {
    try {
      BufferedImage image = new BufferedImage(MAX_WIDTH, MAX_HEIGHT, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        draw(g, spec);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "png", out)) {
        throw new IOException("no PNG writer");
      }
      return out.toByteArray();
    } catch (IOException | RuntimeException | LinkageError e) {
      throw new ChartRenderException("Chart could not be drawn", e);
    }
  }

  private void draw(Graphics2D g, ChartSpec spec) {
    g.setClip(0, 0, MAX_WIDTH, MAX_HEIGHT);
    g.setColor(BACKGROUND);
    g.fillRect(0, 0, MAX_WIDTH, MAX_HEIGHT);
    g.setFont(ChartFonts.bold(TITLE_SIZE));
    g.setColor(TEXT);
    g.drawString(spec.title(), LEFT, 26);

    int plotRight = MAX_WIDTH - RIGHT;
    int markerStrip = 0;
    List<PlacedLabel> labels = new ArrayList<>();
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    if (!spec.markers().isEmpty()) {
      labels = placeLabels(g, spec, plotRight);
      int lanes = labels.stream().mapToInt(l -> l.lane + 1).max().orElse(0);
      markerStrip = lanes * LABEL_LANE + 4;
    }
    int plotTop = TOP + markerStrip;
    int plotBottom = MAX_HEIGHT - AXIS_HEIGHT - (spec.legend().isEmpty() ? 0 : LEGEND_HEIGHT);
    List<ChartSpec.Marker> shown = labels.stream().map(l -> l.marker).toList();
    int panels = Math.max(1, spec.panels().size());
    int panelHeight = (plotBottom - plotTop - PANEL_GAP * (panels - 1)) / panels;
    Scale time = new Scale(0, Math.max(1, spec.endSeconds()), LEFT, plotRight);

    for (int i = 0; i < spec.panels().size(); i++) {
      int top = plotTop + i * (panelHeight + PANEL_GAP);
      drawPanel(g, spec, spec.panels().get(i), time, top, top + panelHeight, shown);
    }
    drawTimeAxis(g, time, plotBottom);
    for (PlacedLabel label : labels) {
      drawMarkerLabel(g, label, TOP + 2);
    }
    drawLegend(g, spec.legend());
  }

  private void drawPanel(
      Graphics2D g,
      ChartSpec spec,
      ChartSpec.Panel panel,
      Scale time,
      int top,
      int bottom,
      List<ChartSpec.Marker> markers) {
    Scale value = valueScale(panel, top, bottom);
    Rectangle plot = new Rectangle(LEFT, top, MAX_WIDTH - RIGHT - LEFT, bottom - top);
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    for (double tick : ticks(value.min, value.max, (bottom - top) / TICK_PIXELS)) {
      int y = value.at(tick);
      g.setColor(tick == 0 ? ZERO_LINE : GRID);
      g.drawLine(LEFT, y, plot.x + plot.width, y);
      g.setColor(MUTED);
      String text = ChartFormat.value(panel.format(), tick);
      g.drawString(text, LEFT - 8 - fm.stringWidth(text), y + fm.getAscent() / 2 - 1);
    }
    Stream.concat(spec.bands().stream(), panel.bands().stream())
        .forEach(band -> drawBand(g, band, time, plot));
    for (ChartSpec.Marker marker : markers) {
      drawMarkerLine(g, marker, time.at(marker.seconds()), top, bottom);
    }
    Rectangle clip = g.getClipBounds();
    g.setClip(plot);
    panel.series().forEach(s -> drawSeries(g, s, time, value, plot));
    drawPins(g, panel, time, value, plot);
    g.setClip(clip);
    g.setColor(TEXT);
    g.setFont(ChartFonts.bold(LABEL_SIZE));
    g.drawString(panel.label(), LEFT + 8, top + g.getFontMetrics().getAscent() + 2);
    drawCaptions(g, panel, plot);
  }

  private void drawPins(
      Graphics2D g, ChartSpec.Panel panel, Scale time, Scale value, Rectangle plot) {
    if (panel.pins().isEmpty()) {
      return;
    }
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    ChartSpec.Series line = panel.series().isEmpty() ? null : panel.series().get(0);
    List<int[]> taken = new ArrayList<>();
    int firstBaseline = plot.y + 2 * fm.getAscent() + 8;
    List<ChartSpec.Pin> ordered =
        panel.pins().stream().sorted(Comparator.comparingDouble(ChartSpec.Pin::seconds)).toList();
    for (ChartSpec.Pin pin : ordered) {
      int x = time.at(pin.seconds());
      int y = value.at(line == null ? 0 : valueAt(line, pin.seconds()));
      if (pin.label() != null && !pin.label().isBlank()) {
        pinLabel(g, fm, pin, x, y, plot, firstBaseline, taken);
      }
      drawShape(g, pin.shape(), x, y, pin.color());
    }
  }

  private void pinLabel(
      Graphics2D g,
      FontMetrics fm,
      ChartSpec.Pin pin,
      int x,
      int y,
      Rectangle plot,
      int firstBaseline,
      List<int[]> taken) {
    int width = fm.stringWidth(pin.label()) + 6;
    if (width > plot.width) {
      return;
    }
    int left = Math.max(plot.x, Math.min(x - 3, plot.x + plot.width - width));
    for (int lane = 0; lane < PIN_LANES; lane++) {
      int baseline = firstBaseline + lane * PIN_LANE_HEIGHT;
      if (baseline > plot.y + plot.height - 4) {
        return;
      }
      int at = lane;
      if (taken.stream().anyMatch(t -> t[0] == at && left < t[2] + 4 && t[1] < left + width + 4)) {
        continue;
      }
      taken.add(new int[] {lane, left, left + width});
      Stroke old = g.getStroke();
      g.setStroke(new BasicStroke(1f));
      g.setColor(withAlpha(pin.color(), 140));
      g.drawLine(x, baseline + 3, x, y);
      g.setStroke(old);
      g.setColor(withAlpha(BACKGROUND, 220));
      g.fillRect(left, baseline - fm.getAscent(), width, fm.getHeight() - 2);
      g.setColor(pin.color());
      g.drawString(pin.label(), left + 3, baseline);
      return;
    }
  }

  /**
   * The series' value at a time, held at the latest known value before it, or the first known value
   * when it lies before them all.
   */
  static double valueAt(ChartSpec.Series s, double seconds) {
    int n = Math.min(s.seconds().length, s.values().length);
    int first = -1;
    int last = -1;
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(s.values()[i])) {
        continue;
      }
      first = first < 0 ? i : first;
      last = s.seconds()[i] <= seconds ? i : last;
      boolean next = i + 1 < n && !Double.isNaN(s.values()[i + 1]);
      if (next && seconds >= s.seconds()[i] && seconds <= s.seconds()[i + 1]) {
        double span = s.seconds()[i + 1] - s.seconds()[i];
        return span == 0
            ? s.values()[i]
            : s.values()[i]
                + (s.values()[i + 1] - s.values()[i]) * (seconds - s.seconds()[i]) / span;
      }
    }
    if (first < 0) {
      return 0;
    }
    return s.values()[last < 0 ? first : last];
  }

  static void drawShape(Graphics2D g, ChartSpec.PinShape shape, int x, int y, Color color) {
    int r = PIN_RADIUS;
    Polygon p = new Polygon();
    switch (shape) {
      case CROSS -> {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        g.drawLine(x - r, y - r, x + r, y + r);
        g.drawLine(x - r, y + r, x + r, y - r);
        g.setStroke(old);
        return;
      }
      case DIAMOND -> {
        p.addPoint(x, y - r - 1);
        p.addPoint(x + r + 1, y);
        p.addPoint(x, y + r + 1);
        p.addPoint(x - r - 1, y);
      }
      case TRIANGLE -> {
        p.addPoint(x, y - r - 1);
        p.addPoint(x + r + 1, y + r);
        p.addPoint(x - r - 1, y + r);
      }
      case STAR -> {
        for (int i = 0; i < 10; i++) {
          double angle = -Math.PI / 2 + i * Math.PI / 5;
          double radius = (i % 2 == 0 ? r + 3 : (r + 3) * 0.45);
          p.addPoint(
              (int) Math.round(x + radius * Math.cos(angle)),
              (int) Math.round(y + radius * Math.sin(angle)));
        }
      }
    }
    g.setColor(color);
    g.fillPolygon(p);
    g.setColor(BACKGROUND);
    g.drawPolygon(p);
  }

  /** A shaded stretch, at least two pixels wide and never outside the plot. */
  private static void drawBand(Graphics2D g, ChartSpec.Band band, Scale time, Rectangle plot) {
    int x1 = time.at(Math.max(0, band.startSeconds()));
    int x2 = Math.max(x1 + 2, time.at(Math.max(0, band.endSeconds())));
    Rectangle fill = new Rectangle(x1, plot.y, x2 - x1, plot.height).intersection(plot);
    if (!fill.isEmpty()) {
      g.setColor(band.color());
      g.fill(fill);
    }
  }

  private void drawCaptions(Graphics2D g, ChartSpec.Panel panel, Rectangle plot) {
    if (panel.series().isEmpty()) {
      return;
    }
    ChartSpec.Series series = panel.series().get(0);
    g.setFont(ChartFonts.bold(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    drawRight(g, series.positiveCaption(), series.positive(), plot, plot.y + fm.getAscent() + 2);
    drawRight(g, series.negativeCaption(), series.negative(), plot, plot.y + plot.height - 6);
  }

  private static void drawRight(Graphics2D g, String text, Color color, Rectangle plot, int y) {
    if (text == null || text.isBlank()) {
      return;
    }
    g.setColor(color.darker());
    g.drawString(text, plot.x + plot.width - 8 - g.getFontMetrics().stringWidth(text), y);
  }

  private void drawSeries(
      Graphics2D g, ChartSpec.Series s, Scale time, Scale value, Rectangle plot) {
    int zero = value.at(0);
    int n = Math.min(s.seconds().length, s.values().length);
    int start = -1;
    for (int i = 0; i <= n; i++) {
      boolean valid = i < n && !Double.isNaN(s.values()[i]);
      if (valid && start < 0) {
        start = i;
      }
      if (!valid && start >= 0) {
        drawRun(g, s, time, value, plot, zero, start, i - 1);
        start = -1;
      }
    }
  }

  private void drawRun(
      Graphics2D g,
      ChartSpec.Series s,
      Scale time,
      Scale value,
      Rectangle plot,
      int zero,
      int from,
      int to) {
    Polygon area = new Polygon();
    area.addPoint(time.at(s.seconds()[from]), zero);
    for (int i = from; i <= to; i++) {
      area.addPoint(time.at(s.seconds()[i]), value.at(s.values()[i]));
    }
    area.addPoint(time.at(s.seconds()[to]), zero);
    Rectangle clip = g.getClipBounds();
    g.setClip(clip.intersection(new Rectangle(plot.x, plot.y, plot.width, zero - plot.y)));
    g.setColor(withAlpha(s.positive(), 150));
    g.fillPolygon(area);
    g.setClip(
        clip.intersection(new Rectangle(plot.x, zero, plot.width, plot.y + plot.height - zero)));
    g.setColor(withAlpha(s.negative(), 150));
    g.fillPolygon(area);
    g.setClip(clip);
    Stroke old = g.getStroke();
    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(TEXT);
    for (int i = from; i < to; i++) {
      g.drawLine(
          time.at(s.seconds()[i]),
          value.at(s.values()[i]),
          time.at(s.seconds()[i + 1]),
          value.at(s.values()[i + 1]));
    }
    g.setStroke(old);
  }

  private static Color withAlpha(Color c, int alpha) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
  }

  private void drawMarkerLine(Graphics2D g, ChartSpec.Marker marker, int x, int top, int bottom) {
    Stroke old = g.getStroke();
    g.setStroke(
        new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {5, 4}, 0));
    g.setColor(marker.color());
    g.drawLine(x, top, x, bottom);
    g.setStroke(old);
  }

  private record PlacedLabel(ChartSpec.Marker marker, int x, int width, int lane) {}

  private List<PlacedLabel> placeLabels(Graphics2D g, ChartSpec spec, int plotRight) {
    FontMetrics fm = g.getFontMetrics();
    Scale time = new Scale(0, Math.max(1, spec.endSeconds()), LEFT, plotRight);
    List<PlacedLabel> placed = new ArrayList<>();
    List<ChartSpec.Marker> ordered =
        spec.markers().stream()
            .sorted(Comparator.comparingDouble(ChartSpec.Marker::seconds))
            .toList();
    for (ChartSpec.Marker marker : ordered) {
      marker = fitted(marker, fm, plotRight - LEFT);
      int width = fm.stringWidth(marker.label()) + 8;
      int anchor = time.at(marker.seconds());
      int x = Math.max(LEFT, Math.min(anchor - 4, plotRight - width));
      int lane = 0;
      while (lane < MAX_LANES && overlaps(placed, x, width, lane)) {
        lane++;
      }
      if (lane < MAX_LANES) {
        placed.add(new PlacedLabel(marker, x, width, lane));
      }
    }
    return placed;
  }

  /** The marker with a label cut to fit the plot width. */
  private static ChartSpec.Marker fitted(ChartSpec.Marker marker, FontMetrics fm, int plotWidth) {
    String label = marker.label();
    if (fm.stringWidth(label) + 8 <= plotWidth) {
      return marker;
    }
    while (label.length() > 1 && fm.stringWidth(label + "…") + 8 > plotWidth) {
      label = label.substring(0, label.length() - 1);
    }
    return new ChartSpec.Marker(marker.seconds(), label + "…", marker.color());
  }

  private static boolean overlaps(List<PlacedLabel> placed, int x, int width, int lane) {
    return placed.stream()
        .anyMatch(p -> p.lane == lane && x < p.x + p.width + 4 && p.x < x + width + 4);
  }

  private void drawMarkerLabel(Graphics2D g, PlacedLabel label, int stripTop) {
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    int y = stripTop + label.lane * LABEL_LANE;
    g.setColor(label.marker.color());
    g.fillRoundRect(label.x, y, label.width, LABEL_LANE - 3, 6, 6);
    g.setColor(Color.WHITE);
    g.drawString(label.marker.label(), label.x + 4, y + fm.getAscent() + 1);
  }

  private void drawTimeAxis(Graphics2D g, Scale time, int plotBottom) {
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    double step = time.max > 3600 ? 600 : 300;
    for (double t = 0; t <= time.max; t += step) {
      int x = time.at(t);
      g.setColor(MUTED);
      g.drawLine(x, plotBottom, x, plotBottom + 4);
      String text = ChartFormat.clock(t);
      g.drawString(text, x - fm.stringWidth(text) / 2, plotBottom + 6 + fm.getAscent());
    }
  }

  private void drawLegend(Graphics2D g, List<ChartSpec.LegendEntry> legend) {
    g.setFont(ChartFonts.plain(LABEL_SIZE));
    FontMetrics fm = g.getFontMetrics();
    int x = LEFT;
    int y = MAX_HEIGHT - 10;
    for (ChartSpec.LegendEntry entry : legend) {
      if (entry.shape() == null) {
        g.setColor(entry.color());
        g.fillRect(x, y - 11, 14, 12);
      } else {
        drawShape(g, entry.shape(), x + 7, y - 5, entry.color());
      }
      g.setColor(TEXT);
      g.drawString(entry.text(), x + 20, y);
      x += 20 + fm.stringWidth(entry.text()) + 24;
    }
  }

  /** Zero stays inside; a side that never has values gets only a small margin. */
  private static Scale valueScale(ChartSpec.Panel panel, int top, int bottom) {
    double high = 0;
    double low = 0;
    for (ChartSpec.Series s : panel.series()) {
      for (double v : s.values()) {
        if (!Double.isNaN(v)) {
          high = Math.max(high, v);
          low = Math.min(low, v);
        }
      }
    }
    double span = Math.max(1, Math.max(high, -low));
    double upper = high > 0 ? niceCeil(high * 1.1) : MIN_MARGIN * span;
    double lower = low < 0 ? -niceCeil(-low * 1.1) : -Math.max(MIN_MARGIN * upper, 0.0);
    return new Scale(lower, upper, bottom - 2, top + 2);
  }

  private static double niceCeil(double v) {
    double magnitude = Math.pow(10, Math.floor(Math.log10(v)));
    for (double m : new double[] {1, 2, 2.5, 5, 10}) {
      if (v <= m * magnitude) {
        return m * magnitude;
      }
    }
    return 10 * magnitude;
  }

  private static List<Double> ticks(double min, double max, int wanted) {
    double raw = (max - min) / Math.max(2, wanted);
    double step = niceCeil(raw);
    List<Double> ticks = new ArrayList<>();
    for (double t = Math.ceil(min / step) * step; t <= max + 1e-9; t += step) {
      ticks.add(Math.abs(t) < 1e-9 ? 0.0 : t);
    }
    return ticks;
  }

  /** Maps a data range to pixels; the pixel range may run backwards. */
  private record Scale(double min, double max, int pixelStart, int pixelEnd) {
    int at(double v) {
      double clamped = Math.max(min, Math.min(max, v));
      return (int) Math.round(pixelStart + (clamped - min) / (max - min) * (pixelEnd - pixelStart));
    }
  }
}
