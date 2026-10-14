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

  static <T> List<String> render(
      String header,
      List<T> rows,
      Comparator<T> byWorth,
      Comparator<T> chronological,
      int cap,
      int maxLength,
      Function<T, String> line) {
    if (rows == null) {
      return List.of();
    }
    List<T> listed =
        rows.stream()
            .filter(Objects::nonNull)
            .sorted(byWorth)
            .limit(cap)
            .sorted(chronological)
            .toList();
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
