package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Finds the trips of one team's heroes away from its group. Every 5 s the group is the biggest
 * cluster of alive heroes; a hero outside it, alive and not farming the jungle, is away. A trip
 * ends when the hero joins the group, starts to farm the jungle, dies or buys back, the group
 * disappears, or the track ends. A trip shorter than the threshold is dropped.
 */
public final class AwayTrips {
  private static final int ALIVE_BIT = 1;

  /**
   * @param traces the team's samples by player slot
   * @param activity the stored activity segments by slot, in time order
   * @param endings when each hero died (an Aegis revival excluded) or bought back, by slot
   */
  public record Inputs(
      Map<Long, Trace> traces,
      Map<Long, List<ActivitySegment>> activity,
      Map<Long, List<Double>> endings) {}

  private final TeamClusters clusters;
  private final ZoneLookup zones;
  private final int awaySeconds;

  public AwayTrips(TeamClusters clusters, ZoneLookup zones, int awaySeconds) {
    this.clusters = clusters;
    this.zones = zones;
    this.awaySeconds = awaySeconds;
  }

  private static final class Open {
    final int start;
    final Zone from;
    final Zone groupZone;
    int lastAway;
    Zone lastZone;

    Open(int start, Zone from, Zone groupZone) {
      this.start = start;
      this.from = from;
      this.groupZone = groupZone;
      this.lastAway = start;
    }
  }

  public List<AwayTrip> find(Inputs in) {
    if (in.traces().isEmpty()) {
      return List.of();
    }
    Trace first = in.traces().values().iterator().next();
    int step = first.stepSeconds();
    int from = in.traces().values().stream().mapToInt(Trace::startSeconds).min().orElse(0);
    int to =
        in.traces().values().stream()
            .mapToInt(t -> t.startSeconds() + (t.x().length - 1) * t.stepSeconds())
            .max()
            .orElse(0);
    Map<Long, Open> open = new TreeMap<>();
    List<AwayTrip> trips = new ArrayList<>();
    Map<Long, Zone> lastSeen = new HashMap<>();
    List<Long> slots = in.traces().keySet().stream().sorted().toList();
    for (int t = from; t <= to; t += step) {
      Map<Long, Point> alive = alivePositions(in.traces(), t);
      Optional<TeamCluster> cluster = clusters.find(alive);
      for (Long slot : slots) {
        Point at = alive.get(slot);
        boolean away =
            cluster.isPresent()
                && at != null
                && !cluster.get().members().contains(slot)
                && !farmingJungle(in.activity().get(slot), t);
        Open o = open.get(slot);
        if (away) {
          Zone here = zones.at(at.x(), at.y());
          if (o == null) {
            o = new Open(t, lastSeen.getOrDefault(slot, here), cluster.get().zone());
            open.put(slot, o);
          }
          o.lastAway = t;
          o.lastZone = here;
        } else if (o != null) {
          open.remove(slot);
          End end = endReason(at, cluster, in.activity().get(slot), t);
          Zone arrival = at == null ? null : zones.at(at.x(), at.y());
          close(trips, in, slot, o, t, end, end == End.REJOINED ? arrival : o.lastZone);
        }
        if (at != null) {
          lastSeen.put(slot, zones.at(at.x(), at.y()));
        } else {
          lastSeen.remove(slot);
        }
      }
    }
    open.forEach(
        (slot, o) -> close(trips, in, slot, o, o.lastAway + step, End.GAME_END, o.lastZone));
    trips.sort(Comparator.comparingInt(AwayTrip::startSeconds).thenComparingLong(AwayTrip::slot));
    return trips;
  }

  private static End endReason(
      Point at, Optional<TeamCluster> cluster, List<ActivitySegment> activity, int time) {
    if (at == null) {
      return End.DIED;
    }
    if (cluster.isEmpty()) {
      return End.NO_GROUP;
    }
    return farmingJungle(activity, time) ? End.FARMING : End.REJOINED;
  }

  private void close(
      List<AwayTrip> trips, Inputs in, long slot, Open o, int sampleEnd, End end, Zone to) {
    int endSeconds = sampleEnd;
    End reason = end;
    for (double ending : in.endings().getOrDefault(slot, List.of())) {
      if (ending > o.start && ending <= sampleEnd) {
        endSeconds = (int) Math.round(ending);
        reason = End.DIED;
        break;
      }
    }
    if (endSeconds - o.start < awaySeconds) {
      return;
    }
    trips.add(
        new AwayTrip(
            slot,
            o.start,
            endSeconds,
            o.from,
            to == null ? o.lastZone : to,
            o.groupZone,
            teleportEnded(in.activity().get(slot), o.start, endSeconds),
            reason));
  }

  private Map<Long, Point> alivePositions(Map<Long, Trace> traces, int time) {
    Map<Long, Point> alive = new HashMap<>();
    traces.forEach(
        (slot, trace) -> {
          int i = Math.round((float) (time - trace.startSeconds()) / trace.stepSeconds());
          if (i >= 0
              && i < trace.x().length
              && trace.level()[i] > 0
              && (trace.flags()[i] & ALIVE_BIT) != 0) {
            alive.put(slot, new Point(trace.x()[i], trace.y()[i]));
          }
        });
    return alive;
  }

  private static boolean farmingJungle(List<ActivitySegment> segments, int time) {
    return segmentAt(segments, time).map(s -> s.activity() == Activity.JUNGLE_FARM).orElse(false);
  }

  private static Optional<ActivitySegment> segmentAt(List<ActivitySegment> segments, int time) {
    if (segments == null) {
      return Optional.empty();
    }
    return segments.stream()
        .filter(s -> s.startSeconds() <= time && time < s.endSeconds())
        .findFirst();
  }

  private static boolean teleportEnded(List<ActivitySegment> segments, int start, int end) {
    return segments != null
        && segments.stream()
            .anyMatch(
                s ->
                    s.activity() == Activity.TELEPORTING
                        && s.endSeconds() >= start
                        && s.endSeconds() <= end);
  }
}
