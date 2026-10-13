package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Optional;

/** Which away trips are rotations, which are worth reporting, and how they turned out. */
public final class RotationRules {
  private RotationRules() {}

  /**
   * A trip that ends in a lane other than the hero's own (any lane for a hero with none) and other
   * than the one he left: a hero who stayed put while the group came to him did not rotate.
   */
  public static boolean isRotation(AwayTrip trip, Zone.Lane ownLane) {
    Zone to = trip.to();
    boolean sameLaneAsLeft =
        trip.from().kind() == Zone.Kind.LANE && trip.from().lane() == to.lane();
    return to.kind() == Zone.Kind.LANE && to.lane() != ownLane && !sameLaneAsLeft;
  }

  /** Plain walking between lanes is not reported: something must have come of it. */
  public static boolean worthReporting(
      AwayTrip trip, TripOutcome outcome, Optional<LaneProxy.Result> proxy) {
    return trip.end() == End.DIED
        || outcome.kills() > 0
        || outcome.fights() > 0
        || proxy.map(LaneProxy.Result::lost).orElse(false);
  }

  public static RotationVerdict verdict(
      AwayTrip trip, TripOutcome outcome, Optional<LaneProxy.Result> proxy) {
    if (trip.end() == End.DIED) {
      return RotationVerdict.DIED;
    }
    if (outcome.kills() > 0 || outcome.fightsWon() > 0) {
      return RotationVerdict.GOOD;
    }
    if (proxy.map(LaneProxy.Result::lost).orElse(false)) {
      return RotationVerdict.WASTED;
    }
    return RotationVerdict.NEUTRAL;
  }
}
