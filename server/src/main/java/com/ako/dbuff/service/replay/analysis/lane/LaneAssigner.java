package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hero's lane is the lane zone where he spent most of his time alive in the assignment span, as
 * long as that is a large enough share and more than his time farming the jungle; a hero who roamed
 * or jungled has no lane and is absent from the result.
 */
final class LaneAssigner {
  private final LaneProperties properties;

  LaneAssigner(LaneProperties properties) {
    this.properties = properties;
  }

  Map<Long, Zone.Lane> assign(Map<Long, List<ActivitySegment>> segmentsBySlot) {
    Map<Long, Zone.Lane> lanes = new HashMap<>();
    segmentsBySlot.forEach(
        (slot, segments) -> {
          Zone.Lane lane = laneOf(segments);
          if (lane != null) {
            lanes.put(slot, lane);
          }
        });
    return lanes;
  }

  Zone.Lane laneOf(List<ActivitySegment> segments) {
    Map<Zone.Lane, Integer> byLane = new EnumMap<>(Zone.Lane.class);
    int alive = 0;
    int jungle = 0;
    for (ActivitySegment s : segments) {
      int seconds =
          Math.min(s.endSeconds(), properties.getAssignToSeconds())
              - Math.max(s.startSeconds(), properties.getAssignFromSeconds());
      if (seconds <= 0 || s.activity() == Activity.DEAD) {
        continue;
      }
      alive += seconds;
      if (s.activity() == Activity.JUNGLE_FARM) {
        jungle += seconds;
      }
      if (s.zone() == Zone.Kind.LANE && s.lane() != null) {
        byLane.merge(s.lane(), seconds, Integer::sum);
      }
    }
    Zone.Lane best = null;
    int bestSeconds = 0;
    for (Map.Entry<Zone.Lane, Integer> e : byLane.entrySet()) {
      if (e.getValue() > bestSeconds) {
        best = e.getKey();
        bestSeconds = e.getValue();
      }
    }
    if (best == null || alive == 0 || jungle >= bestSeconds) {
      return null;
    }
    return bestSeconds / (double) alive >= properties.getMinLaneShare() ? best : null;
  }
}
