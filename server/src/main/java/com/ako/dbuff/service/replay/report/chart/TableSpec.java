package com.ako.dbuff.service.replay.report.chart;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * What a table picture shows: a title, column headers and rows of cells. A row with fewer cells
 * than there are headers leaves the rest empty; a null cell is an empty cell and a null header an
 * empty header.
 */
public record TableSpec(String title, List<String> headers, List<List<Cell>> rows) {

  public TableSpec {
    headers = headers.stream().map(h -> h == null ? "" : h).toList();
    rows = rows.stream().map(r -> Collections.unmodifiableList(new ArrayList<>(r))).toList();
  }

  /**
   * One table cell.
   *
   * @param icon PNG bytes drawn left of the text, or null
   * @param note small text after the main one, such as "#14", or null
   * @param band colour of a stripe at the cell's left edge (a team), or null
   */
  public record Cell(byte[] icon, String text, String note, Color band) {

    public static Cell text(String text) {
      return new Cell(null, text, null, null);
    }
  }

  /** A hash of everything the picture shows, so a cache key can tell two tables apart. */
  public String fingerprint() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      text(out, title);
      out.writeInt(headers.size());
      for (String header : headers) {
        text(out, header);
      }
      out.writeInt(rows.size());
      for (List<Cell> row : rows) {
        out.writeInt(row.size());
        for (Cell cell : row) {
          if (cell == null) {
            out.writeInt(-2);
            continue;
          }
          out.writeInt(cell.icon() == null ? -1 : cell.icon().length);
          if (cell.icon() != null) {
            out.write(cell.icon());
          }
          text(out, cell.text());
          text(out, cell.note());
          out.writeInt(cell.band() == null ? 0 : cell.band().getRGB());
        }
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
}
