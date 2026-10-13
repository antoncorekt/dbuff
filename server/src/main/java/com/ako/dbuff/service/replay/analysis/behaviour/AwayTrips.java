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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Finds the trips of one team's heroes away from its group. Every 5 s the group is the biggest
 * cluster of alive heroes; a hero outside it, alive and not farming the jungle, is away. A trip
 * ends when the hero joins the group, starts to farm the jungle, dies or buys back, the group
 * disappears, or the track ends. A trip shorter than the threshold is dropped unless the hero died
 * or bought back in it. A trip that starts in the base after a death or buyback is a respawn, not a
 * rotation, and is dropped.
 */
public final class AwayTrips {
  /**
   * @param traces the team's samples by player slot
   * @param activity the stored activity segments by slot, in time order
   * @param deaths when each hero died (an Aegis revival excluded), by slot
   * @param buybacks when each hero bought back, by slot
   */
  public record Inputs(
      Map<Long, Trace> traces,
      Map<Long, List<ActivitySegment>> activity,
      Map<Long, List<Double>> deaths,
      Map<Long, List<Double>> buybacks) {}

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
    final boolean respawn;
    final List<Integer> times = new ArrayList<>();
    final List<Zone> seen = new ArrayList<>();
    int lastAway;

    Open(int start, Zone from, Zone groupZone, boolean respawn) {
      this.start = start;
      this.from = from;
      this.groupZone = groupZone;
      this.respawn = respawn;
      this.lastAway = start;
    }

    Zone lastZone() {
      return seen.get(seen.size() - 1);
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
    Set<Long> respawning = new HashSet<>();
    List<Long> slots = in.traces().keySet().stream().sorted().toList();
    for (int t = from; t <= to; t += step) {
      Map<Long, Point> alive = TraceSamples.alive(in.traces(), t);
      Set<Long> present = TraceSamples.present(in.traces(), t);
      Optional<TeamCluster> cluster = clusters.find(alive);
      for (Long slot : slots) {
        Point at = alive.get(slot);
        if (present.contains(slot) && (at == null || endedBetween(in, slot, t - step, t))) {
          respawning.add(slot);
        }
        boolean away =
            cluster.isPresent()
                && at != null
                && !cluster.get().members().contains(slot)
                && !TraceSamples.farmingJungle(in.activity().get(slot), t);
        Open o = open.get(slot);
        if (away) {
          Zone here = zones.at(at.x(), at.y());
          if (o == null) {
            o =
                new Open(
                    t,
                    lastSeen.getOrDefault(slot, here),
                    cluster.get().zone(),
                    respawning.contains(slot));
            open.put(slot, o);
          }
          o.lastAway = t;
          o.times.add(t);
          o.seen.add(here);
        } else if (o != null) {
          open.remove(slot);
          End end = endReason(at, present.contains(slot), cluster, in.activity().get(slot), t);
          Zone arrival = at == null ? null : zones.at(at.x(), at.y());
          close(trips, in, slot, o, t, end, end == End.REJOINED ? arrival : o.lastZone());
        }
        if (!away && at != null) {
          respawning.remove(slot);
        }
        if (at != null) {
          lastSeen.put(slot, zones.at(at.x(), at.y()));
        } else {
          lastSeen.remove(slot);
        }
      }
    }
    open.forEach(
        (slot, o) -> close(trips, in, slot, o, o.lastAway + step, End.GAME_END, o.lastZone()));
    trips.sort(Comparator.comparingInt(AwayTrip::startSeconds).thenComparingLong(AwayTrip::slot));
    return trips;
  }

  private static boolean endedBetween(Inputs in, long slot, double after, double upTo) {
    return Stream.of(in.deaths(), in.buybacks())
        .flatMap(m -> m.getOrDefault(slot, List.of()).stream())
        .anyMatch(e -> e > after && e <= upTo);
  }

  private static End endReason(
      Point at,
      boolean present,
      Optional<TeamCluster> cluster,
      List<ActivitySegment> activity,
      int time) {
    if (at == null) {
      return present ? End.DIED : End.TRACK_ENDED;
    }
    if (cluster.isEmpty()) {
      return End.NO_GROUP;
    }
    return TraceSamples.farmingJungle(activity, time) ? End.FARMING : End.REJOINED;
  }

  private void close(
      List<AwayTrip> trips, Inputs in, long slot, Open o, int sampleEnd, End end, Zone to) {
    int endSeconds = sampleEnd;
    End reason = end;
    double bestEnding = Double.MAX_VALUE;
    for (double d : in.deaths().getOrDefault(slot, List.of())) {
      if (d > o.start && d <= sampleEnd && d < bestEnding) {
        bestEnding = d;
        reason = End.DIED;
      }
    }
    for (double b : in.buybacks().getOrDefault(slot, List.of())) {
      if (b > o.start && b <= sampleEnd && b < bestEnding) {
        bestEnding = b;
        reason = End.BOUGHT_BACK;
      }
    }
    if (bestEnding != Double.MAX_VALUE) {
      endSeconds = (int) Math.round(bestEnding);
    }
    boolean died = reason == End.DIED || reason == End.BOUGHT_BACK;
    if (o.respawn && o.from.kind() == Zone.Kind.BASE
        || !died && endSeconds - o.start < awaySeconds) {
      return;
    }
    Zone destination = to == null ? o.lastZone() : to;
    trips.add(
        new AwayTrip(
            slot,
            o.start,
            arrival(o, destination, endSeconds),
            endSeconds,
            o.from,
            destination,
            o.groupZone,
            teleportEnded(in.activity().get(slot), o.start, endSeconds),
            reason));
  }

  private static int arrival(Open o, Zone destination, int endSeconds) {
    for (int i = 0; i < o.seen.size(); i++) {
      Zone z = o.seen.get(i);
      if (z.kind() == destination.kind() && z.lane() == destination.lane()) {
        return Math.min(o.times.get(i), endSeconds);
      }
    }
    return endSeconds;
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
