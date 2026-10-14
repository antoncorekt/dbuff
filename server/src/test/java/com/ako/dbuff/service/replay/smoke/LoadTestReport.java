package com.ako.dbuff.service.replay.smoke;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The load test result as a readable table, for the console and for the file sent to the owner. */
final class LoadTestReport {
  private final List<LoadTestRun> runs;
  private final long baselineMb;
  private final long maxHeapMb;
  private final double totalSeconds;
  private final Map<String, List<Long>> rowsPerTable;

  LoadTestReport(
      List<LoadTestRun> runs,
      long baselineMb,
      long maxHeapMb,
      double totalSeconds,
      Map<String, List<Long>> rowsPerTable) {
    this.runs = runs;
    this.baselineMb = baselineMb;
    this.maxHeapMb = maxHeapMb;
    this.totalSeconds = totalSeconds;
    this.rowsPerTable = new TreeMap<>(rowsPerTable);
  }

  String render() {
    StringBuilder out = new StringBuilder("# Replay load test\n\n");
    out.append(
        String.format(
            Locale.ROOT,
            "%d replays one after another, max heap %d MB, heap after the context started and"
                + " before the first run %d MB, total %.0f s (%.1f min).%n%n",
            runs.size(),
            maxHeapMb,
            baselineMb,
            totalSeconds,
            totalSeconds / 60));
    out.append(
        "| match | mode | minutes | seconds | heap after GC MB | peak MB (all pools) | peak old"
            + " gen MB | status | rows |\n|---|---|---|---|---|---|---|---|---|\n");
    runs.forEach(run -> out.append(row(run)));
    out.append(
        "\n"
            + "`heap after GC` is the live heap after the run including everything the test context"
            + " holds; `peak` is the sum of the pools' peaks since the run began, so it is an upper"
            + " bound.\n");
    String notes =
        runs.stream()
            .filter(run -> !run.note().isEmpty())
            .map(run -> "- " + run.matchId() + " " + run.status() + ": " + run.note())
            .collect(Collectors.joining("\n"));
    if (!notes.isEmpty()) {
      out.append("\n## Notes\n\n").append(notes).append('\n');
    }
    out.append(
        "\n"
            + "## Rows per table over all runs (min / median / max)\n\n"
            + "| table | min | median | max |\n"
            + "|---|---|---|---|\n");
    rowsPerTable.forEach(
        (table, counts) -> {
          List<Long> sorted = counts.stream().sorted().toList();
          out.append(
              String.format(
                  Locale.ROOT,
                  "| %s | %d | %d | %d |%n",
                  table,
                  sorted.getFirst(),
                  sorted.get(sorted.size() / 2),
                  sorted.getLast()));
        });
    return out.toString();
  }

  private static String row(LoadTestRun run) {
    return String.format(
        Locale.ROOT,
        "| %d | %s | %d | %.1f | %d | %d | %d | %s | %d |%n",
        run.matchId(),
        run.mode(),
        run.minutes(),
        run.seconds(),
        run.liveMb(),
        run.peakMb(),
        run.peakOldMb(),
        run.status(),
        run.rows());
  }
}
