package com.ako.dbuff.service.replay.looprunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What changed between two reports of one match, read from the tables a topic names. A named table
 * missing from a report that was produced is an error, not an empty table.
 */
public final class ReviewLoopDiff {
  /**
   * A table compared between rounds.
   *
   * @param heading the {@code ##} heading above the table
   * @param keyLabel the word before the key of a changed row, empty for none
   * @param keyColumns how many leading columns identify a row
   */
  public record Section(String heading, String keyLabel, int keyColumns) {}

  private ReviewLoopDiff() {}

  public static List<String> changes(String previous, String current, List<Section> sections) {
    if (ReviewLoopReport.isUnavailable(current)) {
      return List.of("skipped this round (" + ReviewLoopReport.reasonOf(current) + ")");
    }
    List<String> out = new ArrayList<>();
    for (Section s : sections) {
      out.addAll(
          compare(
              s.heading(),
              s.keyLabel(),
              ReviewLoopReport.isUnavailable(previous)
                  ? new Table(List.of(), Map.of(), s.keyColumns())
                  : table(previous, s.heading(), s.keyColumns()),
              table(current, s.heading(), s.keyColumns())));
    }
    return out;
  }

  private record Table(List<String> header, Map<String, List<String>> rows, int keyColumns) {}

  private static List<String> compare(String heading, String keyLabel, Table before, Table after) {
    boolean bothPresent = !before.header().isEmpty() && !after.header().isEmpty();
    boolean extended = extendedBy(before.header(), after.header());
    if (bothPresent && !extended && !before.header().equals(after.header())) {
      return List.of(
          "format changed in %s: columns %s -> %s"
              .formatted(heading, before.header(), after.header()));
    }
    List<String> out = new ArrayList<>();
    Set<String> keys = new LinkedHashSet<>(before.rows().keySet());
    keys.addAll(after.rows().keySet());
    for (String key : keys) {
      String label = keyLabel.isEmpty() ? key : keyLabel + " " + key;
      List<String> was = before.rows().get(key);
      List<String> is = after.rows().get(key);
      if (was == null || is == null) {
        out.add(label + ": " + (was == null ? "added" : "removed"));
      } else if (was.size() != before.header().size()
          || is.size() != after.header().size()
          || (!extended && was.size() != is.size())) {
        out.add(
            "format changed in %s, %s: %d -> %d cells, header has %d"
                .formatted(heading, label, was.size(), is.size(), after.header().size()));
      } else {
        for (int i = after.keyColumns(); i < was.size(); i++) {
          if (!was.get(i).equals(is.get(i))) {
            out.add(
                "%s, %s: %s -> %s".formatted(label, after.header().get(i), was.get(i), is.get(i)));
          }
        }
      }
    }
    return out;
  }

  /** The later report only added columns after the earlier ones, so the shared ones compare. */
  private static boolean extendedBy(List<String> before, List<String> after) {
    return !before.isEmpty()
        && after.size() > before.size()
        && after.subList(0, before.size()).equals(before);
  }

  private static Table table(String report, String heading, int keyColumns) {
    Map<String, List<String>> rows = new LinkedHashMap<>();
    List<String> header = List.of();
    boolean inSection = false;
    boolean seenHeader = false;
    boolean found = false;
    for (String raw : report.split("\\R")) {
      String line = raw.strip();
      if (line.startsWith("## ")) {
        inSection = line.substring(3).strip().equals(heading);
        found |= inSection;
        continue;
      }
      if (!inSection || !line.startsWith("|") || line.startsWith("|---")) {
        continue;
      }
      List<String> cells = cells(line);
      if (!seenHeader) {
        header = cells;
        seenHeader = true;
      } else if (cells.size() >= keyColumns) {
        rows.put(String.join(" ", cells.subList(0, keyColumns)), cells);
      }
    }
    if (!found) {
      throw new IllegalStateException("the report has no '## " + heading + "' section");
    }
    return new Table(header, rows, keyColumns);
  }

  /** The cells of a table row; a backslash escapes the pipe or the backslash that follows. */
  private static List<String> cells(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    for (int i = 1; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\\' && i + 1 < line.length()) {
        cell.append(line.charAt(++i));
      } else if (c == '|') {
        cells.add(cell.toString().strip());
        cell.setLength(0);
      } else {
        cell.append(c);
      }
    }
    if (!cell.toString().isBlank()) {
      cells.add(cell.toString().strip());
    }
    return cells;
  }
}
