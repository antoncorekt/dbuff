package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns minute labels into stretches. Equal labels in neighbouring minutes join. A stretch shorter
 * than the minimum is absorbed by its longer neighbour (the one before on a tie), shortest first,
 * so a one-minute dip does not cut a long stretch in two. Minutes without a label break a run, and
 * a run that is still too short on its own is dropped.
 */
public final class SplitStretches {
  private static final int MINUTE = 60;

  private final int minMinutes;

  public SplitStretches(int minMinutes) {
    this.minMinutes = minMinutes;
  }

  private static final class Run {
    int start;
    int minutes;
    final Posture posture;

    Run(int start, int minutes, Posture posture) {
      this.start = start;
      this.minutes = minutes;
      this.posture = posture;
    }
  }

  public List<Stretch> of(List<MinutePosture> minutes) {
    List<Stretch> stretches = new ArrayList<>();
    List<MinutePosture> segment = new ArrayList<>();
    for (MinutePosture m : minutes) {
      if (!segment.isEmpty() && m.minute() != segment.getLast().minute() + 1) {
        stretches.addAll(close(segment));
        segment.clear();
      }
      segment.add(m);
    }
    stretches.addAll(close(segment));
    return stretches;
  }

  private List<Stretch> close(List<MinutePosture> segment) {
    List<Run> runs = new ArrayList<>();
    for (MinutePosture m : segment) {
      if (!runs.isEmpty() && runs.getLast().posture == m.posture()) {
        runs.getLast().minutes++;
      } else {
        runs.add(new Run(m.minute(), 1, m.posture()));
      }
    }
    while (runs.size() > 1 && absorbShortest(runs)) {
      mergeEqualNeighbours(runs);
    }
    return runs.stream()
        .filter(r -> r.minutes >= minMinutes)
        .map(r -> new Stretch(r.start * MINUTE, (r.start + r.minutes) * MINUTE, r.posture))
        .toList();
  }

  private boolean absorbShortest(List<Run> runs) {
    int shortest = 0;
    for (int i = 1; i < runs.size(); i++) {
      if (runs.get(i).minutes < runs.get(shortest).minutes) {
        shortest = i;
      }
    }
    Run run = runs.get(shortest);
    if (run.minutes >= minMinutes) {
      return false;
    }
    boolean intoPrevious =
        shortest > 0
            && (shortest == runs.size() - 1
                || runs.get(shortest - 1).minutes >= runs.get(shortest + 1).minutes);
    if (intoPrevious) {
      runs.get(shortest - 1).minutes += run.minutes;
    } else {
      Run next = runs.get(shortest + 1);
      next.minutes += run.minutes;
      next.start = run.start;
    }
    runs.remove(shortest);
    return true;
  }

  private static void mergeEqualNeighbours(List<Run> runs) {
    for (int i = runs.size() - 1; i > 0; i--) {
      if (runs.get(i).posture == runs.get(i - 1).posture) {
        runs.get(i - 1).minutes += runs.get(i).minutes;
        runs.remove(i);
      }
    }
  }
}
