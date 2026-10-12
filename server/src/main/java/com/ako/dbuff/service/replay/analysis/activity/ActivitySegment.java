package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.service.replay.map.Zone;

/**
 * A stretch of game time with one activity in one kind of place; it covers {@code [start, end)}.
 *
 * @param lane set when the zone is a lane
 * @param side whose half of the map it is, from the hero's point of view; null in the river
 */
public record ActivitySegment(
    int startSeconds,
    int endSeconds,
    Activity activity,
    Zone.Kind zone,
    Zone.Lane lane,
    Side side) {

  public static ActivitySegment of(PlayerActivitySegmentDomain row) {
    return new ActivitySegment(
        row.getStartSeconds(),
        row.getEndSeconds(),
        Activity.valueOf(row.getActivity()),
        Zone.Kind.valueOf(row.getZone()),
        row.getLane() == null ? null : Zone.Lane.valueOf(row.getLane()),
        row.getSide() == null ? null : Side.valueOf(row.getSide()));
  }

  public enum Side {
    OWN,
    ENEMY
  }
}
