package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;

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
      AwayTrip trip, TripOutcome outcome, RotationVerdict verdict) {
    return trip.end() == End.DIED
        || outcome.kills() > 0
        || outcome.fights() > 0
        || verdict == RotationVerdict.WASTED;
  }

  /**
   * Death first, then a kill or a won fight; else wasted when the lane he left was lost, when he
   * did nothing and his income dropped, or when a tower fell while he was away.
   */
  public static RotationVerdict verdict(
      AwayTrip trip, TripOutcome outcome, List<RotationReason> reasons) {
    if (trip.end() == End.DIED) {
      return RotationVerdict.DIED;
    }
    if (outcome.kills() > 0 || outcome.fightsWon() > 0) {
      return RotationVerdict.GOOD;
    }
    if (reasons.contains(RotationReason.LEFT_LANE_UNDEFENDED)
        || reasons.contains(RotationReason.TOWER_LOST_WHILE_AWAY)
        || (reasons.contains(RotationReason.DID_NOTHING)
            && reasons.contains(RotationReason.INCOME_DROP))) {
      return RotationVerdict.WASTED;
    }
    return RotationVerdict.NEUTRAL;
  }
}
