package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hero with no lane of his own who stayed in the lane of his teammates (a support who walked with
 * the carry, or who spent the first minutes elsewhere) gets that lane. Over the laning window of a
 * lane his team holds, the seconds he was alive and either in the lane zone or within {@code
 * shadow-distance} of a teammate who has the lane must be at least {@code shadow-min-share} of his
 * time alive in that window, and more than his jungle farming there; a roamer or a jungler fails
 * that and stays without a lane.
 */
final class LaneShadowAssigner {
  private final LaneProperties properties;

  LaneShadowAssigner(LaneProperties properties) {
    this.properties = properties;
  }

  Map<Long, Zone.Lane> assign(
      Map<Long, List<ActivitySegment>> segmentsBySlot,
      Map<Long, TeamSide> teams,
      Map<Long, ReplayPlayerTrackDomain> tracks,
      List<LaneHero> holders) {
    Map<Long, Zone.Lane> lanes = new HashMap<>();
    Map<TeamSide, Map<Zone.Lane, List<LaneHero>>> byTeamAndLane = byTeamAndLane(holders);
    segmentsBySlot.forEach(
        (slot, segments) -> {
          ReplayPlayerTrackDomain track = tracks.get(slot);
          TeamSide team = teams.get(slot);
          if (track == null || team == null || holders.stream().anyMatch(h -> h.slot() == slot)) {
            return;
          }
          Zone.Lane lane =
              laneOf(segments, track, byTeamAndLane.getOrDefault(team, Map.of()), tracks);
          if (lane != null) {
            lanes.put(slot, lane);
          }
        });
    return lanes;
  }

  private static Map<TeamSide, Map<Zone.Lane, List<LaneHero>>> byTeamAndLane(
      List<LaneHero> holders) {
    Map<TeamSide, Map<Zone.Lane, List<LaneHero>>> byTeam = new EnumMap<>(TeamSide.class);
    for (LaneHero h : holders) {
      if (h.window().length() > 0) {
        byTeam
            .computeIfAbsent(h.team(), k -> new EnumMap<>(Zone.Lane.class))
            .computeIfAbsent(h.lane(), k -> new ArrayList<>())
            .add(h);
      }
    }
    return byTeam;
  }

  private Zone.Lane laneOf(
      List<ActivitySegment> segments,
      ReplayPlayerTrackDomain track,
      Map<Zone.Lane, List<LaneHero>> teamLanes,
      Map<Long, ReplayPlayerTrackDomain> tracks) {
    Zone.Lane best = null;
    int bestSeconds = 0;
    for (Map.Entry<Zone.Lane, List<LaneHero>> lane : teamLanes.entrySet()) {
      int from = lane.getValue().stream().mapToInt(h -> h.window().startSeconds()).min().orElse(0);
      int to = lane.getValue().stream().mapToInt(h -> h.window().endSeconds()).max().orElse(0);
      int alive = 0;
      int jungle = 0;
      int together = 0;
      int step = track.getStepSeconds();
      for (int i = 0; i < track.getX().length; i++) {
        int second = track.getStartSeconds() + i * step;
        ActivitySegment s = segmentAt(segments, second);
        if (second < from || second >= to || s == null || s.activity() == Activity.DEAD) {
          continue;
        }
        alive += step;
        if (s.activity() == Activity.JUNGLE_FARM) {
          jungle += step;
        }
        if (inLane(s, lane.getKey()) || near(track, i, second, lane.getValue(), tracks)) {
          together += step;
        }
      }
      if (together >= properties.getShadowMinSeconds()
          && together > jungle
          && together >= properties.getShadowMinShare() * alive
          && together > bestSeconds) {
        best = lane.getKey();
        bestSeconds = together;
      }
    }
    return best;
  }

  private static boolean inLane(ActivitySegment segment, Zone.Lane lane) {
    return segment.zone() == Zone.Kind.LANE && segment.lane() == lane;
  }

  private boolean near(
      ReplayPlayerTrackDomain track,
      int index,
      int second,
      List<LaneHero> partners,
      Map<Long, ReplayPlayerTrackDomain> tracks) {
    for (LaneHero partner : partners) {
      ReplayPlayerTrackDomain other = tracks.get(partner.slot());
      if (other == null || !partner.window().contains(second)) {
        continue;
      }
      int j = (second - other.getStartSeconds()) / other.getStepSeconds();
      if (j >= 0 && j < other.getX().length) {
        double dx = track.getX()[index] - other.getX()[j];
        double dy = track.getY()[index] - other.getY()[j];
        if (Math.hypot(dx, dy) <= properties.getShadowDistance()) {
          return true;
        }
      }
    }
    return false;
  }

  private static ActivitySegment segmentAt(List<ActivitySegment> segments, int second) {
    for (ActivitySegment s : segments) {
      if (second >= s.startSeconds() && second < s.endSeconds()) {
        return s;
      }
    }
    return null;
  }
}
