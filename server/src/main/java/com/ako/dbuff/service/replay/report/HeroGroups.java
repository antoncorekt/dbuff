package com.ako.dbuff.service.replay.report;

import java.util.ArrayList;
import java.util.List;

/** Cuts the heroes of a graph into the groups that share one picture. */
final class HeroGroups {
  private HeroGroups() {}

  /** Consecutive groups of {@code size} (at least 1), the last one holding what is left. */
  static <T> List<List<T>> of(List<T> items, int size) {
    int step = Math.max(1, size);
    List<List<T>> groups = new ArrayList<>();
    for (int from = 0; from < items.size(); from += step) {
      groups.add(items.subList(from, Math.min(items.size(), from + step)));
    }
    return groups;
  }
}
