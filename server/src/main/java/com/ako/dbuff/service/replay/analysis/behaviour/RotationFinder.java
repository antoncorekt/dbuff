package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Turns the stored facts of a match into its rotations. */
public final class RotationFinder {
  /**
   * @param deaths when each hero died (an Aegis revival excluded), by slot
   * @param buybacks when each hero bought back, by slot
   * @param lanes the stored lanes of the heroes that have one
   */
  public record Inputs(
      Map<Long, Trace> traces,
      Map<Long, List<ActivitySegment>> activity,
      Map<Long, List<Double>> deaths,
      Map<Long, List<Double>> buybacks,
      List<LaneSlot> lanes,
      TripOutcomes.Events events) {}

  private final BehaviourProperties properties;
  private final ZoneLookup zones;

  public RotationFinder(BehaviourProperties properties, ZoneLookup zones) {
    this.properties = properties;
    this.zones = zones;
  }

  public List<Rotation> find(Inputs in) {
    TeamClusters clusters =
        new TeamClusters(properties.getClusterDistance(), properties.getMinClusterSize(), zones);
    AwayTrips trips = new AwayTrips(clusters, zones, properties.getAwaySeconds());
    TripOutcomes outcomes = new TripOutcomes(properties.getOutcomeLingerSeconds());
    LaneProxy proxy = new LaneProxy(properties.getProxyWindowSeconds());
    List<Rotation> rotations = new ArrayList<>();
    for (TeamSide team : TeamSide.values()) {
      Map<Long, Trace> traces =
          in.traces().entrySet().stream()
              .filter(e -> TeamSide.ofSlot(e.getKey()) == team)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      for (AwayTrip trip :
          trips.find(new AwayTrips.Inputs(traces, in.activity(), in.deaths(), in.buybacks()))) {
        Zone.Lane laneLeft = laneLeft(in.lanes(), trip);
        if (!RotationRules.isRotation(trip, laneLeft)) {
          continue;
        }
        TripOutcome outcome = outcomes.of(trip, team, in.events());
        Optional<LaneProxy.Result> result =
            proxy.of(trip.slot(), trip.startSeconds(), in.lanes(), in.events().creeps());
        if (RotationRules.worthReporting(trip, outcome, result)) {
          rotations.add(
              new Rotation(
                  team,
                  trip,
                  laneLeft,
                  outcome,
                  result,
                  RotationRules.verdict(trip, outcome, result)));
        }
      }
    }
    return rotations;
  }

  private static Zone.Lane laneLeft(List<LaneSlot> lanes, AwayTrip trip) {
    return lanes.stream()
        .filter(l -> l.slot() == trip.slot())
        .filter(
            l -> l.startSeconds() <= trip.startSeconds() && trip.startSeconds() <= l.endSeconds())
        .map(LaneSlot::lane)
        .findFirst()
        .orElse(null);
  }
}
