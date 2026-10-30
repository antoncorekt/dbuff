package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.Nearest;
import com.ako.dbuff.service.replay.analysis.behaviour.ObjectiveConversion.RoshanNote;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeEvents.RoshanKill;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
   * @param skipped fights left out because they started too early or the winner was too far from
   *     any standing tower for a miss to mean anything
   */
  record Found(List<ObjectiveConversion> conversions, int cutShort, int skipped) {}

  private record Taken(
      ObjectiveKind kind, String key, double seconds, TeamSide taker, Long roshanKiller) {}

  private final BehaviourProperties properties;
  private final ZoneLookup zones;

  ObjectiveFinder(BehaviourProperties properties, ZoneLookup zones) {
    this.properties = properties;
    this.zones = zones;
  }

  Found find(List<ObjectiveFight> fights, Map<Long, Trace> traces, Events events) {
    List<ObjectiveFight> ordered =
        fights.stream().sorted(Comparator.comparingDouble(ObjectiveFight::startSeconds)).toList();
    Map<Integer, Taken> takenBy = firstClaims(ordered, events);
    List<ObjectiveConversion> found = new ArrayList<>();
    int cutShort = 0;
    int skipped = 0;
    for (ObjectiveFight fight : ordered) {
      Map<Long, Point> alive = winnersAlive(fight, traces);
      Taken taken = takenBy.get(fight.index());
      if (taken != null) {
        found.add(converted(fight, taken, alive, traces));
      } else if (fight.endSeconds() + properties.getObjectiveWindowSeconds()
          > events.gameEndSeconds()) {
        cutShort++;
      } else if (fight.startSeconds() < properties.getObjectiveMinFightSeconds()) {
        skipped++;
      } else {
        ObjectiveConversion miss = missed(fight, alive, events);
        if (tooFar(miss)) {
          skipped++;
        } else {
          found.add(miss);
        }
      }
    }
    return new Found(found, cutShort, skipped);
  }

  /**
   * Each objective goes to the earliest fight whose window covers it and whose winner took it; a
   * fight gets the earliest objective among those. The map is by fight index.
   */
  private Map<Integer, Taken> firstClaims(List<ObjectiveFight> ordered, Events events) {
    Map<Integer, Taken> claims = new HashMap<>();
    for (Taken taken : objectives(events)) {
      ordered.stream()
          .filter(f -> f.winner() == taken.taker())
          .filter(
              f ->
                  within(
                      taken.seconds(),
                      f.startSeconds(),
                      f.endSeconds() + properties.getObjectiveWindowSeconds()))
          .findFirst()
          .ifPresent(f -> claims.merge(f.index(), taken, ObjectiveFinder::earlier));
    }
    return claims;
  }

  private static Taken earlier(Taken a, Taken b) {
    return Comparator.comparingDouble(Taken::seconds).thenComparing(Taken::kind).compare(a, b) <= 0
        ? a
        : b;
  }

  private static List<Taken> objectives(Events events) {
    Stream<Taken> buildings =
        events.buildings().stream()
            .filter(b -> !b.denied() && ObjectiveKind.ofBuilding(b.kind()) != null)
            .map(
                b ->
                    new Taken(
                        ObjectiveKind.ofBuilding(b.kind()),
                        b.name(),
                        b.seconds(),
                        b.owner().opponent(),
                        null));
    Stream<Taken> roshan =
        events.roshanKills().stream()
            .map(
                r ->
                    new Taken(
                        ObjectiveKind.ROSHAN,
                        SmokeEvents.ROSHAN,
                        r.seconds(),
                        TeamSide.ofSlot(r.slot()),
                        r.slot()));
    return Stream.concat(buildings, roshan)
        .sorted(Comparator.comparingDouble(Taken::seconds))
        .toList();
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

  private boolean tooFar(ObjectiveConversion miss) {
    return miss.nearest() != null
        && miss.nearest().distance() >= properties.getObjectiveMaxTowerDistance();
  }

  private Optional<Nearest> nearest(ObjectiveFight fight, Map<Long, Point> alive, Events events) {
    List<TowerFall> fallen =
        events.buildings().stream()
            .filter(b -> b.owner() == fight.winner().opponent())
            .filter(b -> b.seconds() <= fight.endSeconds())
            .toList();
    Optional<Nearest> best = Optional.empty();
    for (StandingTower tower : events.towers()) {
      if (tower.owner() != fight.winner().opponent() || tower.fellIn(fallen)) {
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
    return null;
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
