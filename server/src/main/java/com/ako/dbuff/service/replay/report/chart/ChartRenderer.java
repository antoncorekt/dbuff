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
    int panels = Math.max(1, spec.panels().size());
    int panelHeight = (plotBottom - plotTop - PANEL_GAP * (panels - 1)) / panels;
    Scale time = new Scale(0, Math.max(1, spec.endSeconds()), LEFT, plotRight);

    for (int i = 0; i < spec.panels().size(); i++) {
      int top = plotTop + i * (panelHeight + PANEL_GAP);
      drawPanel(g, spec, spec.panels().get(i), time, top, top + panelHeight);
    }
    drawTimeAxis(g, time, plotBottom);
    for (PlacedLabel label : labels) {
      drawMarkerLabel(g, label, TOP + 2);
    }
    drawLegend(g, spec.legend());
  }

  private void drawPanel(
      Graphics2D g, ChartSpec spec, ChartSpec.Panel panel, Scale time, int top, int bottom) {
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
    for (ChartSpec.Band band : spec.bands()) {
      int x1 = time.at(Math.max(0, band.startSeconds()));
      int x2 = Math.max(x1 + 2, time.at(Math.min(spec.endSeconds(), band.endSeconds())));
      g.setColor(band.color());
      g.fillRect(x1, top, x2 - x1, bottom - top);
    }
    for (ChartSpec.Marker marker : spec.markers()) {
      drawMarkerLine(g, marker, time.at(marker.seconds()), top, bottom);
    }
    Rectangle clip = g.getClipBounds();
    g.setClip(plot);
    panel.series().forEach(s -> drawSeries(g, s, time, value, plot));
    g.setClip(clip);
    g.setColor(TEXT);
    g.setFont(ChartFonts.bold(LABEL_SIZE));
    g.drawString(panel.label(), LEFT + 8, top + g.getFontMetrics().getAscent() + 2);
    drawCaptions(g, panel, plot);
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
      int width = fm.stringWidth(marker.label()) + 8;
      int anchor = time.at(marker.seconds());
      int x = Math.max(LEFT, Math.min(anchor - 4, plotRight - width));
      int lane = 0;
      while (lane < MAX_LANES - 1 && overlaps(placed, x, width, lane)) {
        lane++;
      }
      placed.add(new PlacedLabel(marker, x, width, lane));
    }
    return placed;
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
      g.setColor(entry.color());
      g.fillRect(x, y - 11, 14, 12);
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
