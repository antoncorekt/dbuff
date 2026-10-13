package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Optional;

/**
 * Finds when one hero's laning starts and ends. It starts when he first stands in his lane zone
 * (not before 0:00). It ends at the first of: he left, the lane's tower fell, the cap.
 *
 * <p>He left when a stretch outside the lane, without death time, either holds {@code awaySeconds}
 * of farming the jungle or another lane, or lasts {@code returnSeconds} in all (a teleport or a
 * trip to the base he did not come back from within that time). The end is when the stretch began.
 */
final class LaningWindows {
  private final LaneProperties properties;

  LaningWindows(LaneProperties properties) {
    this.properties = properties;
  }

  /**
   * @param towerFallSecond when a tower of the lane fell, null if none did
   * @param trackEndSeconds the last second the hero's activity is known for
   */
  Optional<LaningWindow> find(
      List<ActivitySegment> segments,
      Zone.Lane lane,
      Integer towerFallSecond,
      int trackEndSeconds) {
    Integer start = null;
    int streakStart = -1;
    int hardSeconds = 0;
    boolean teleported = false;
    LaneEnd leftFor = null;
    int leftAt = -1;
    for (ActivitySegment s : segments) {
      if (s.activity() == Activity.DEAD || s.endSeconds() <= 0) {
        continue;
      }
      int from = Math.max(s.startSeconds(), 0);
      boolean home = isHome(s, lane);
      if (start == null) {
        if (home) {
          start = from;
        }
        continue;
      }
      if (home) {
        streakStart = -1;
        hardSeconds = 0;
        teleported = false;
        continue;
      }
      if (streakStart < 0) {
        streakStart = from;
      }
      teleported |= s.activity() == Activity.TELEPORTING;
      LaneEnd hard = hardAway(s);
      if (hard != null) {
        hardSeconds += s.endSeconds() - from;
        if (hardSeconds >= properties.getAwaySeconds()) {
          leftFor = hard;
        }
      }
      if (leftFor == null && s.endSeconds() - streakStart >= properties.getReturnSeconds()) {
        leftFor = teleported ? LaneEnd.TELEPORTED : LaneEnd.LEFT;
      }
      if (leftFor != null) {
        leftAt = streakStart;
        break;
      }
    }
    if (start == null) {
      return Optional.empty();
    }
    return Optional.of(window(start, leftAt, leftFor, towerFallSecond, trackEndSeconds));
  }

  private LaningWindow window(
      int start, int leftAt, LaneEnd leftFor, Integer towerFallSecond, int trackEndSeconds) {
    int end = trackEndSeconds;
    LaneEnd reason = LaneEnd.GAME_END;
    if (leftFor != null && leftAt < end) {
      end = leftAt;
      reason = leftFor;
    }
    if (towerFallSecond != null && towerFallSecond < end) {
      end = towerFallSecond;
      reason = LaneEnd.TOWER_FELL;
    }
    if (properties.getCapSeconds() < end) {
      end = properties.getCapSeconds();
      reason = LaneEnd.CAP;
    }
    return new LaningWindow(start, Math.max(start, end), reason);
  }

  private static boolean isHome(ActivitySegment s, Zone.Lane lane) {
    return s.zone() == Zone.Kind.LANE && s.lane() == lane;
  }

  /** Farming the jungle or another lane, as opposed to passing through or waiting. */
  private static LaneEnd hardAway(ActivitySegment s) {
    if (s.activity() == Activity.JUNGLE_FARM) {
      return LaneEnd.JUNGLE;
    }
    boolean otherLane = s.zone() == Zone.Kind.LANE && s.lane() != null;
    return otherLane && s.activity() != Activity.TRAVELING && s.activity() != Activity.TELEPORTING
        ? LaneEnd.OTHER_LANE
        : null;
  }
}
