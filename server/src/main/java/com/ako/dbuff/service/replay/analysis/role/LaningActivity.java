package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where a hero spent the laning phase, as shares of the time he was alive and out of the base: in
 * the mid lane, farming the jungle, and in lanes other than the one he was in most.
 */
record LaningActivity(double midShare, double jungleShare, double roamShare) {
  static final LaningActivity NONE = new LaningActivity(0, 0, 0);

  static LaningActivity of(List<ActivitySegment> segments, int endSeconds) {
    int active = 0;
    int jungle = 0;
    int inLanes = 0;
    Map<Zone.Lane, Integer> byLane = new EnumMap<>(Zone.Lane.class);
    for (ActivitySegment s : segments) {
      int seconds = Math.min(s.endSeconds(), endSeconds) - Math.max(s.startSeconds(), 0);
      if (seconds <= 0 || s.activity() == Activity.DEAD || s.zone() == Zone.Kind.BASE) {
        continue;
      }
      active += seconds;
      if (s.activity() == Activity.JUNGLE_FARM) {
        jungle += seconds;
      }
      if (s.zone() == Zone.Kind.LANE && s.lane() != null) {
        inLanes += seconds;
        byLane.merge(s.lane(), seconds, Integer::sum);
      }
    }
    if (active == 0) {
      return NONE;
    }
    int main = byLane.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    return new LaningActivity(
        byLane.getOrDefault(Zone.Lane.MID, 0) / (double) active,
        jungle / (double) active,
        (inLanes - main) / (double) active);
  }
}
