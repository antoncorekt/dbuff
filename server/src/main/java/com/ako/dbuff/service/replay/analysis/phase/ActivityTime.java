package com.ako.dbuff.service.replay.analysis.phase;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** How long a player spent in each activity inside a window. */
public final class ActivityTime {
  private ActivityTime() {}

  public record Span(int startSeconds, int endSeconds, Activity activity) {}

  /** Seconds of each activity inside the window; segments are clipped to it. */
  public static Map<Activity, Integer> within(List<Span> spans, int from, int to) {
    Map<Activity, Integer> seconds = new EnumMap<>(Activity.class);
    for (Span span : spans) {
      int overlap = Math.min(to, span.endSeconds()) - Math.max(from, span.startSeconds());
      if (overlap > 0) {
        seconds.merge(span.activity(), overlap, Integer::sum);
      }
    }
    return seconds;
  }
}
