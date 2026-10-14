package com.ako.dbuff.service.replay.smoke;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

/**
 * Heap figures of one run: live bytes after a forced GC, and the peak the memory pools saw. The
 * live figure is approximate: {@code System.gc()} is a hint (the figures were taken with the
 * default collector and without {@code -XX:+DisableExplicitGC}, where it runs a full collection).
 * The pool peaks include garbage the previous run left behind, so they are upper bounds, not the
 * live set during the run.
 */
final class HeapProbe {
  private static final long MB = 1024 * 1024;

  private final List<MemoryPoolMXBean> pools =
      ManagementFactory.getMemoryPoolMXBeans().stream()
          .filter(pool -> pool.getType() == MemoryType.HEAP)
          .toList();

  void resetPeak() {
    pools.forEach(MemoryPoolMXBean::resetPeakUsage);
  }

  /**
   * Sum of the peaks of every heap pool: an upper bound, since the pools peak at different times.
   */
  long peakMb() {
    return pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum() / MB;
  }

  /** Peak of the old generation, the closest cheap figure to the live set during the run. */
  long peakOldMb() {
    return pools.stream()
            .filter(pool -> pool.getName().contains("Old") || pool.getName().contains("Tenured"))
            .mapToLong(pool -> pool.getPeakUsage().getUsed())
            .sum()
        / MB;
  }

  static long liveMbAfterGc() {
    for (int i = 0; i < 3; i++) {
      System.gc();
    }
    Runtime runtime = Runtime.getRuntime();
    return (runtime.totalMemory() - runtime.freeMemory()) / MB;
  }
}
