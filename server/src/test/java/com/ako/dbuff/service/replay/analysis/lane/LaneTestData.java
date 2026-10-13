package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;

final class LaneTestData {
  private LaneTestData() {}

  static ActivitySegment inLane(int from, int to, Zone.Lane lane) {
    return new ActivitySegment(from, to, Activity.LANE_FARM, Zone.Kind.LANE, lane, null);
  }

  static ActivitySegment jungleFarm(int from, int to) {
    return new ActivitySegment(from, to, Activity.JUNGLE_FARM, Zone.Kind.JUNGLE, null, null);
  }

  static ActivitySegment teleporting(int from, int to) {
    return new ActivitySegment(from, to, Activity.TELEPORTING, Zone.Kind.BASE, null, null);
  }

  static ActivitySegment inBase(int from, int to) {
    return new ActivitySegment(from, to, Activity.IN_BASE, Zone.Kind.BASE, null, null);
  }

  static ActivitySegment dead(int from, int to) {
    return new ActivitySegment(from, to, Activity.DEAD, Zone.Kind.BASE, null, null);
  }

  static ActivitySegment inRiver(int from, int to) {
    return new ActivitySegment(from, to, Activity.POSITIONED, Zone.Kind.RIVER, null, null);
  }

  static ActivitySegment passing(int from, int to, Zone.Lane lane) {
    return new ActivitySegment(from, to, Activity.TRAVELING, Zone.Kind.LANE, lane, null);
  }
}
