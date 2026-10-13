package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the heroes' tracks and activity at one game second. */
final class TraceSamples {
  private static final int ALIVE_BIT = 1;

  private TraceSamples() {}

  /** Heroes that are in the game (level above 0), dead or alive. */
  static Set<Long> present(Map<Long, Trace> traces, int time) {
    Set<Long> present = new HashSet<>();
    traces.forEach(
        (slot, trace) -> {
          int i = index(trace, time);
          if (i >= 0 && i < trace.x().length && trace.level()[i] > 0) {
            present.add(slot);
          }
        });
    return present;
  }

  static Map<Long, Point> alive(Map<Long, Trace> traces, int time) {
    Map<Long, Point> alive = new HashMap<>();
    traces.forEach(
        (slot, trace) -> {
          int i = index(trace, time);
          if (i >= 0 && i < trace.x().length && livingAt(trace, i)) {
            alive.put(slot, new Point(trace.x()[i], trace.y()[i]));
          }
        });
    return alive;
  }

  /** Whether the hero is in the game and alive at sample {@code i}. */
  static boolean livingAt(Trace trace, int i) {
    return trace.level()[i] > 0 && (trace.flags()[i] & ALIVE_BIT) != 0;
  }

  static boolean farmingJungle(List<ActivitySegment> segments, int time) {
    return segmentAt(segments, time).map(s -> s.activity() == Activity.JUNGLE_FARM).orElse(false);
  }

  private static int index(Trace trace, int time) {
    return Math.round((float) (time - trace.startSeconds()) / trace.stepSeconds());
  }

  private static Optional<ActivitySegment> segmentAt(List<ActivitySegment> segments, int time) {
    if (segments == null) {
      return Optional.empty();
    }
    return segments.stream()
        .filter(s -> s.startSeconds() <= time && time < s.endSeconds())
        .findFirst();
  }
}
