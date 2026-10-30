package com.ako.dbuff.service.replay.report.chart;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Draws a {@link TableSpec} into PNG bytes, off screen. The picture is at most {@link #MAX_WIDTH}
 * wide; its height grows with the rows up to {@link #MAX_HEIGHT}, so a table of more than {@link
 * #MAX_ROWS} rows is refused and the caller splits it over several pictures.
 */
@Slf4j
@Component
public class TableRenderer {
  public static final int MAX_WIDTH = 1000;
  public static final int MAX_HEIGHT = 1800;
  public static final int ICON_PX = 32;
  public static final int ROW_HEIGHT = 40;
  public static final int MAX_ROWS = (MAX_HEIGHT - 80) / ROW_HEIGHT;

  private static final Color BACKGROUND = new Color(0xFFFFFF);
  private static final Color STRIPE = new Color(0xF6F8FA);
  private static final Color TEXT = new Color(0x1F2328);
  private static final Color MUTED = new Color(0x59636E);
  private static final Color GRID = new Color(0xD0D7DE);
  private static final float TITLE_SIZE = 18;
  private static final float TEXT_SIZE = 14;
  private static final float NOTE_SIZE = 11;
  private static final int MARGIN = 16;
  private static final int TITLE_HEIGHT = 44;
  private static final int HEADER_HEIGHT = 30;
  private static final int PAD = 8;
  private static final int BAND_WIDTH = 5;
  private static final int ICON_GAP = 6;
  private static final int NOTE_GAP = 6;
  private static final int MIN_COLUMN = 40;

  static {
    System.setProperty("java.awt.headless", "true");
  }

  /** The PNG, or empty when the table is empty, too long, or drawing failed. */
  public Optional<byte[]> render(TableSpec spec) {
    if (spec.headers().isEmpty() || spec.rows().isEmpty() || spec.rows().size() > MAX_ROWS) {
      return Optional.empty();
    }
    try {
      int height = TITLE_HEIGHT + HEADER_HEIGHT + spec.rows().size() * ROW_HEIGHT + MARGIN;
      BufferedImage image = new BufferedImage(MAX_WIDTH, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      int width;
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int[] columns = columnWidths(g, spec);
        width = Math.min(MAX_WIDTH, 2 * MARGIN + sum(columns));
        draw(g, spec, columns, width, height);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image.getSubimage(0, 0, width, height), "png", out)) {
        throw new IOException("no PNG writer");
      }
      return Optional.of(out.toByteArray());
    } catch (IOException | RuntimeException | LinkageError e) {
      log.warn("Table {} could not be drawn: {}", spec.title(), e.toString(), e);
      return Optional.empty();
    }
  }

  private void draw(Graphics2D g, TableSpec spec, int[] columns, int width, int height) {
    g.setColor(BACKGROUND);
    g.fillRect(0, 0, width, height);
    g.setColor(TEXT);
    g.setFont(ChartFonts.bold(TITLE_SIZE));
    g.drawString(fit(g, spec.title(), width - 2 * MARGIN), MARGIN, 30);

    int headerTop = TITLE_HEIGHT;
    g.setFont(ChartFonts.bold(TEXT_SIZE));
    g.setColor(MUTED);
    int x = MARGIN;
    for (int c = 0; c < columns.length; c++) {
      String header = c < spec.headers().size() ? spec.headers().get(c) : "";
      g.drawString(
          fit(g, header, columns[c] - 2 * PAD),
          x + PAD,
          headerTop + (HEADER_HEIGHT + g.getFontMetrics().getAscent()) / 2 - 2);
      x += columns[c];
    }
    g.setColor(GRID);
    g.drawLine(MARGIN, headerTop + HEADER_HEIGHT, width - MARGIN, headerTop + HEADER_HEIGHT);

    int top = headerTop + HEADER_HEIGHT;
    for (int r = 0; r < spec.rows().size(); r++) {
      int y = top + r * ROW_HEIGHT;
      if (r % 2 == 1) {
        g.setColor(STRIPE);
        g.fillRect(MARGIN, y, width - 2 * MARGIN, ROW_HEIGHT);
      }
      x = MARGIN;
      List<TableSpec.Cell> row = spec.rows().get(r);
      for (int c = 0; c < columns.length; c++) {
        if (c < row.size() && row.get(c) != null) {
          drawCell(g, row.get(c), x, y, columns[c]);
        }
        x += columns[c];
      }
    }
    g.setColor(GRID);
    int bottom = top + spec.rows().size() * ROW_HEIGHT;
    g.drawLine(MARGIN, bottom, width - MARGIN, bottom);
  }

  private void drawCell(Graphics2D g, TableSpec.Cell cell, int x, int y, int columnWidth) {
    if (cell.band() != null) {
      g.setColor(cell.band());
      g.fillRect(x, y + 4, BAND_WIDTH, ROW_HEIGHT - 8);
    }
    int left = x + PAD + (cell.band() != null ? BAND_WIDTH : 0);
    int right = x + columnWidth - PAD;
    BufferedImage icon = decode(cell.icon());
    if (icon != null) {
      g.drawImage(icon, left, y + (ROW_HEIGHT - ICON_PX) / 2, ICON_PX, ICON_PX, null);
      left += ICON_PX + ICON_GAP;
    }
    g.setFont(ChartFonts.plain(TEXT_SIZE));
    FontMetrics fm = g.getFontMetrics();
    String note = cell.note() == null || cell.note().isBlank() ? null : cell.note();
    int noteWidth = 0;
    if (note != null) {
      g.setFont(ChartFonts.plain(NOTE_SIZE));
      noteWidth = g.getFontMetrics().stringWidth(note) + NOTE_GAP;
      g.setFont(ChartFonts.plain(TEXT_SIZE));
    }
    String text = fit(g, cell.text() == null ? "" : cell.text(), right - left - noteWidth);
    int baseline = y + (ROW_HEIGHT + fm.getAscent()) / 2 - 2;
    g.setColor(TEXT);
    g.drawString(text, left, baseline);
    if (note != null) {
      g.setFont(ChartFonts.plain(NOTE_SIZE));
      g.setColor(MUTED);
      g.drawString(note, left + fm.stringWidth(text) + NOTE_GAP, baseline);
    }
  }

  private int[] columnWidths(Graphics2D g, TableSpec spec) {
    int count = spec.headers().size();
    int[] natural = new int[count];
    g.setFont(ChartFonts.bold(TEXT_SIZE));
    for (int c = 0; c < count; c++) {
      natural[c] = g.getFontMetrics().stringWidth(spec.headers().get(c)) + 2 * PAD;
    }
    for (List<TableSpec.Cell> row : spec.rows()) {
      for (int c = 0; c < Math.min(count, row.size()); c++) {
        if (row.get(c) != null) {
          natural[c] = Math.max(natural[c], cellWidth(g, row.get(c)));
        }
      }
    }
    int available = MAX_WIDTH - 2 * MARGIN;
    int total = sum(natural);
    if (total <= available) {
      return natural;
    }
    int[] shrunk = new int[count];
    for (int c = 0; c < count; c++) {
      shrunk[c] = Math.max(MIN_COLUMN, (int) ((long) natural[c] * available / total));
    }
    return shrunk;
  }

  private int cellWidth(Graphics2D g, TableSpec.Cell cell) {
    int width = 2 * PAD + (cell.band() != null ? BAND_WIDTH : 0);
    if (cell.icon() != null) {
      width += ICON_PX + ICON_GAP;
    }
    g.setFont(ChartFonts.plain(TEXT_SIZE));
    width += g.getFontMetrics().stringWidth(cell.text() == null ? "" : cell.text());
    if (cell.note() != null && !cell.note().isBlank()) {
      g.setFont(ChartFonts.plain(NOTE_SIZE));
      width += NOTE_GAP + g.getFontMetrics().stringWidth(cell.note());
    }
    return width;
  }

  private static String fit(Graphics2D g, String text, int width) {
    FontMetrics fm = g.getFontMetrics();
    if (fm.stringWidth(text) <= width) {
      return text;
    }
    String cut = text;
    while (cut.length() > 1 && fm.stringWidth(cut + "…") > width) {
      cut = cut.substring(0, cut.length() - 1);
    }
    return cut + "…";
  }

  private static BufferedImage decode(byte[] png) {
    if (png == null) {
      return null;
    }
    try {
      return ImageIO.read(new ByteArrayInputStream(png));
    } catch (IOException e) {
      return null;
    }
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int v : values) {
      total += v;
    }
    return total;
  }
}
