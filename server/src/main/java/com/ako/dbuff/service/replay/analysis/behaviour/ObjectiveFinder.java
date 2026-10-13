package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.Nearest;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.RoshanNote;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeEvents.RoshanKill;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tells, for each important fight, whether its winner took a tower, barracks, the ancient or Roshan
 * from the fight's start to {@code objective-window-seconds} after its end.
 */
final class ObjectiveFinder {
  private static final int SAMPLE_SECONDS = 5;

  /**
   * @param buildings the towers, barracks and ancients that fell
   * @param roshanKills the Roshan kills by hero
   * @param towers the lane towers of the map
   * @param gameEndSeconds the last second the combat log reaches
   */
  record Events(
      List<TowerFall> buildings,
      List<RoshanKill> roshanKills,
      List<StandingTower> towers,
      double gameEndSeconds) {}

  /**
   * @param cutShort fights left out because nothing was taken and the game ended before the window
   *     ran out, so a miss cannot be told
   */
  record Found(List<ObjectiveConversion> conversions, int cutShort) {}

  private record Taken(ObjectiveKind kind, String key, double seconds, Long roshanKiller) {}

  private final BehaviourProperties properties;
  private final ZoneLookup zones;

  ObjectiveFinder(BehaviourProperties properties, ZoneLookup zones) {
    this.properties = properties;
    this.zones = zones;
  }

  Found find(List<ObjectiveFight> fights, Map<Long, Trace> traces, Events events) {
    List<ObjectiveConversion> found = new ArrayList<>();
    int cutShort = 0;
    for (ObjectiveFight fight : fights) {
      Map<Long, Point> alive = winnersAlive(fight, traces);
      Optional<Taken> taken = taken(fight, events);
      if (taken.isPresent()) {
        found.add(converted(fight, taken.get(), alive, traces));
      } else if (fight.endSeconds() + properties.getObjectiveWindowSeconds()
          > events.gameEndSeconds()) {
        cutShort++;
      } else {
        found.add(missed(fight, alive, events));
      }
    }
    return new Found(found, cutShort);
  }

  private Optional<Taken> taken(ObjectiveFight fight, Events events) {
    double to = fight.endSeconds() + properties.getObjectiveWindowSeconds();
    Stream<Taken> buildings =
        events.buildings().stream()
            .filter(b -> b.owner() == fight.winner().opponent() && !b.denied())
            .filter(b -> within(b.seconds(), fight.startSeconds(), to))
            .filter(b -> ObjectiveKind.ofBuilding(b.kind()) != null)
            .map(b -> new Taken(ObjectiveKind.ofBuilding(b.kind()), b.name(), b.seconds(), null));
    Stream<Taken> roshan =
        events.roshanKills().stream()
            .filter(r -> TeamSide.ofSlot(r.slot()) == fight.winner())
            .filter(r -> within(r.seconds(), fight.startSeconds(), to))
            .map(r -> new Taken(ObjectiveKind.ROSHAN, SmokeEvents.ROSHAN, r.seconds(), r.slot()));
    return Stream.concat(buildings, roshan)
        .min(Comparator.comparingDouble(Taken::seconds).thenComparing(Taken::kind));
  }

  private ObjectiveConversion converted(
      ObjectiveFight fight, Taken taken, Map<Long, Point> alive, Map<Long, Trace> traces) {
    RoshanNote note =
        taken.kind() == ObjectiveKind.ROSHAN
            ? new RoshanNote(
                nearRoshan(fight.winner(), taken, traces), RoshanCall.of(fight.stateBefore()))
            : null;
    return new ObjectiveConversion(
        fight.index(),
        fight.winner(),
        ObjectiveResult.CONVERTED,
        taken.kind(),
        taken.key(),
        (int) Math.max(0, Math.round(taken.seconds() - fight.endSeconds())),
        fight.stateBefore(),
        alive.size(),
        null,
        note);
  }

  private ObjectiveConversion missed(ObjectiveFight fight, Map<Long, Point> alive, Events events) {
    return new ObjectiveConversion(
        fight.index(),
        fight.winner(),
        ObjectiveResult.MISSED,
        null,
        null,
        null,
        fight.stateBefore(),
        alive.size(),
        nearest(fight, alive, events).orElse(null),
        null);
  }

  private Optional<Nearest> nearest(ObjectiveFight fight, Map<Long, Point> alive, Events events) {
    Set<String> fallen = new HashSet<>();
    events.buildings().stream()
        .filter(b -> b.owner() == fight.winner().opponent() && b.name() != null)
        .filter(b -> b.seconds() <= fight.endSeconds())
        .forEach(b -> fallen.add(StandingTower.key(b.name())));
    Optional<Nearest> best = Optional.empty();
    for (StandingTower tower : events.towers()) {
      if (tower.owner() != fight.winner().opponent() || fallen.contains(tower.name())) {
        continue;
      }
      for (Map.Entry<Long, Point> hero : alive.entrySet()) {
        double distance = hero.getValue().distanceTo(tower.position());
        if (best.isEmpty() || distance < best.get().distance()) {
          Point at = hero.getValue();
          best = Optional.of(new Nearest(tower, distance, hero.getKey(), zones.at(at.x(), at.y())));
        }
      }
    }
    return best;
  }

  private List<Long> nearRoshan(TeamSide winner, Taken taken, Map<Long, Trace> traces) {
    for (double time : new double[] {taken.seconds(), taken.seconds() - SAMPLE_SECONDS}) {
      Map<Long, Point> alive = TraceSamples.alive(traces, (int) Math.round(time));
      Point killer = alive.get(taken.roshanKiller());
      if (killer != null) {
        return alive.entrySet().stream()
            .filter(e -> TeamSide.ofSlot(e.getKey()) == winner)
            .filter(e -> e.getValue().distanceTo(killer) <= properties.getRoshanNearRadius())
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
      }
    }
    return List.of();
  }

  private static Map<Long, Point> winnersAlive(ObjectiveFight fight, Map<Long, Trace> traces) {
    Map<Long, Point> alive = TraceSamples.alive(traces, (int) Math.round(fight.endSeconds()));
    alive.keySet().removeIf(slot -> TeamSide.ofSlot(slot) != fight.winner());
    return alive;
  }

  private static boolean within(double seconds, double from, double to) {
    return seconds >= from && seconds <= to;
  }
}
