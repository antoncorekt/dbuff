package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Optional;

/** Reads what the reason rules need about a trip off the stored facts of the match. */
public final class ReasonEvidence {
  private final BehaviourProperties properties;
  private final TripOutcomes outcomes;
  private final IncomeProxy incomeProxy;

  public ReasonEvidence(BehaviourProperties properties, TripOutcomes outcomes) {
    this.properties = properties;
    this.outcomes = outcomes;
    this.incomeProxy = new IncomeProxy(properties.getIncomeTailSeconds());
  }

  public ReasonFacts of(
      AwayTrip trip,
      TeamSide team,
      TripOutcome outcome,
      Optional<LaneProxy.Result> proxy,
      MidGameFacts mid,
      List<FightFact> fights) {
    double until = outcomes.tailEnd(trip);
    List<DamageHit> stayHits =
        mid.hits().stream()
            .filter(h -> h.slot() == trip.slot())
            .filter(h -> h.seconds() >= trip.arrivalSeconds() && h.seconds() <= until)
            .toList();
    return new ReasonFacts(
        trip,
        outcome,
        proxy,
        incomeProxy.of(trip, mid.gold().get(trip.slot()), mid.xp().get(trip.slot())),
        properties.getIncomeShare(),
        (int) stayHits.stream().filter(h -> !h.tower()).count(),
        (int) stayHits.stream().filter(DamageHit::tower).count(),
        outcome.fights() == 0 && missedFight(trip, fights),
        towerLost(trip, team, mid));
  }

  private static boolean missedFight(AwayTrip trip, List<FightFact> fights) {
    return fights.stream()
        .anyMatch(
            f ->
                f.lane() != null
                    && f.lane() == trip.to().lane()
                    && f.end() >= trip.startSeconds()
                    && f.end() < trip.arrivalSeconds());
  }

  private static boolean towerLost(AwayTrip trip, TeamSide team, MidGameFacts mid) {
    return mid.towerLosses().getOrDefault(team, List.of()).stream()
        .anyMatch(t -> t >= trip.startSeconds() && t <= trip.endSeconds());
  }
}
