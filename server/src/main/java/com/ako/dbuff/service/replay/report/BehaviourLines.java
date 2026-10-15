package com.ako.dbuff.service.replay.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The shape the rotations and smoke sections share: choose the rows worth listing by worth, keep at
 * most {@code cap} of them, list them in time order under a header, each line cut to length.
 */
final class BehaviourLines {
  private BehaviourLines() {}

  /** The rows to list, in time order, and how many the cap left out. */
  record Picked<T>(List<T> listed, int leftOut) {}

  /** The {@code cap} best rows by worth, in time order; null rows never. */
  static <T> Picked<T> pick(
      List<T> rows, Comparator<T> byWorth, Comparator<T> chronological, int cap) {
    if (rows == null) {
      return new Picked<>(List.of(), 0);
    }
    List<T> ranked = rows.stream().filter(Objects::nonNull).sorted(byWorth).toList();
    List<T> listed = ranked.stream().limit(cap).sorted(chronological).toList();
    return new Picked<>(listed, ranked.size() - listed.size());
  }

  static <T> List<String> render(
      String header,
      List<T> rows,
      Comparator<T> byWorth,
      Comparator<T> chronological,
      int cap,
      int maxLength,
      Function<T, String> line) {
    return lines(header, pick(rows, byWorth, chronological, cap).listed(), maxLength, line);
  }

  /** The header and one cut line for each of the rows already chosen; nothing for no rows. */
  static <T> List<String> lines(
      String header, List<T> listed, int maxLength, Function<T, String> line) {
    if (listed.isEmpty()) {
      return List.of();
    }
    int max = Math.max(LineText.ELLIPSIS.length() + 1, maxLength);
    List<String> lines = new ArrayList<>();
    lines.add(header);
    listed.forEach(row -> lines.add(LineText.cut(line.apply(row), max)));
    return lines;
  }
}
