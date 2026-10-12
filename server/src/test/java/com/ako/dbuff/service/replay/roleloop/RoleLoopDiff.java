package com.ako.dbuff.service.replay.roleloop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What changed between two reports of one match, read from their roles and verdicts tables. */
final class RoleLoopDiff {
  private static final int ROLE_KEY_COLUMNS = 1;
  private static final int VERDICT_KEY_COLUMNS = 2;

  private RoleLoopDiff() {}

  static List<String> changes(String previous, String current) {
    List<String> out = new ArrayList<>();
    out.addAll(
        compare(
            "slot",
            table(previous, RoleLoopReport.ROLES, ROLE_KEY_COLUMNS),
            table(current, RoleLoopReport.ROLES, ROLE_KEY_COLUMNS)));
    out.addAll(
        compare(
            "",
            table(previous, RoleLoopReport.VERDICTS, VERDICT_KEY_COLUMNS),
            table(current, RoleLoopReport.VERDICTS, VERDICT_KEY_COLUMNS)));
    return out;
  }

  private record Table(List<String> header, Map<String, List<String>> rows, int keyColumns) {}

  private static List<String> compare(String keyLabel, Table before, Table after) {
    List<String> out = new ArrayList<>();
    Set<String> keys = new LinkedHashSet<>(before.rows().keySet());
    keys.addAll(after.rows().keySet());
    for (String key : keys) {
      String label = keyLabel.isEmpty() ? key : keyLabel + " " + key;
      List<String> was = before.rows().get(key);
      List<String> is = after.rows().get(key);
      if (was == null || is == null) {
        out.add(label + ": " + (was == null ? "added" : "removed"));
        continue;
      }
      for (int i = after.keyColumns(); i < is.size(); i++) {
        if (i < was.size() && !was.get(i).equals(is.get(i))) {
          out.add(
              "%s, %s: %s -> %s".formatted(label, after.header().get(i), was.get(i), is.get(i)));
        }
      }
    }
    return out;
  }

  private static Table table(String report, String heading, int keyColumns) {
    Map<String, List<String>> rows = new LinkedHashMap<>();
    List<String> header = List.of();
    boolean inSection = false;
    boolean seenHeader = false;
    for (String line : report.split("\n")) {
      if (line.startsWith("## ")) {
        inSection = line.substring(3).strip().equals(heading);
        continue;
      }
      if (!inSection || !line.startsWith("|") || line.startsWith("|---")) {
        continue;
      }
      List<String> cells =
          Arrays.stream(line.substring(1, line.length() - 1).split("\\|", -1))
              .map(String::strip)
              .toList();
      if (!seenHeader) {
        header = cells;
        seenHeader = true;
      } else {
        rows.put(String.join(" ", cells.subList(0, keyColumns)), cells);
      }
    }
    return new Table(header, rows, keyColumns);
  }
}
