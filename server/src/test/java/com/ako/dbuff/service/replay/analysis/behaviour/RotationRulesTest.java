package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.BASE;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static org.assertj.core.api.Assertions.assertThat;

class RotationRulesTest {

  private static AwayTrip trip(Zone to, End end) {
    return new AwayTrip(0L, 600, 620, 650, MID, to, MID, false, end);
  }

  private static TripOutcome outcome(int kills, int fights, int won) {
    return new TripOutcome(kills, 0, fights, won, fights == 0 ? null : 3);
  }

  @Test
  void isRotation_needsAnotherLanesZone() {
    assertThat(RotationRules.isRotation(trip(TOP, End.REJOINED), Zone.Lane.MID, 120)).isTrue();
    assertThat(RotationRules.isRotation(trip(TOP, End.REJOINED), null, 120)).isTrue();
    assertThat(RotationRules.isRotation(trip(MID, End.REJOINED), Zone.Lane.MID, 120)).isFalse();
    assertThat(RotationRules.isRotation(trip(BASE, End.REJOINED), Zone.Lane.MID, 120)).isFalse();
  }

  @Test
  void isRotation_aTripThatStartsBeforeTheMinimumIsNotOne() {
    AwayTrip early = new AwayTrip(0L, 25, 45, 80, MID, TOP, MID, false, End.REJOINED);
    AwayTrip later = new AwayTrip(0L, 130, 150, 180, MID, TOP, MID, false, End.REJOINED);

    assertThat(RotationRules.isRotation(early, Zone.Lane.MID, 120)).isFalse();
    assertThat(RotationRules.isRotation(later, Zone.Lane.MID, 120)).isTrue();
  }

  @Test
  void isRotation_stayingInTheLaneHeLeftIsNotOne() {
    AwayTrip stayed = new AwayTrip(0L, 600, 620, 650, TOP, TOP, MID, false, End.REJOINED);

    assertThat(RotationRules.isRotation(stayed, null, 120)).isFalse();
  }

  private static final List<RotationReason> NONE = List.of();

  @Test
  void verdict_aKillIsGoodEvenWithBadReasons() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.REJOINED),
                outcome(1, 0, 0),
                List.of(RotationReason.LEFT_LANE_UNDEFENDED, RotationReason.TOWER_LOST_WHILE_AWAY),
                true))
        .isEqualTo(RotationVerdict.GOOD);
  }

  @Test
  void verdict_aWonFightIsGood() {
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 1), NONE, false))
        .isEqualTo(RotationVerdict.GOOD);
  }

  @Test
  void verdict_aLostLaneIsWasted() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.REJOINED),
                outcome(0, 1, 0),
                List.of(RotationReason.LEFT_LANE_UNDEFENDED),
                false))
        .isEqualTo(RotationVerdict.WASTED);
  }

  @Test
  void verdict_aTowerLostWhileAwayWithNoResultIsWasted() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.REJOINED),
                outcome(0, 0, 0),
                List.of(RotationReason.TOWER_LOST_WHILE_AWAY),
                true))
        .isEqualTo(RotationVerdict.WASTED);
  }

  @Test
  void verdict_aTowerLostInAnotherLaneIsListedButDoesNotDecide() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.REJOINED),
                outcome(0, 0, 0),
                List.of(RotationReason.TOWER_LOST_WHILE_AWAY),
                false))
        .isEqualTo(RotationVerdict.NEUTRAL);
  }

  @Test
  void verdict_doingNothingWhileTheFarmDroppedIsWasted() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.REJOINED),
                outcome(0, 0, 0),
                List.of(RotationReason.DID_NOTHING, RotationReason.INCOME_DROP),
                false))
        .isEqualTo(RotationVerdict.WASTED);
  }

  @Test
  void verdict_oneWeakReasonAloneIsNeutral() {
    for (RotationReason reason :
        List.of(
            RotationReason.DID_NOTHING,
            RotationReason.INCOME_DROP,
            RotationReason.ARRIVED_AFTER_FIGHT_ENDED)) {
      assertThat(
              RotationRules.verdict(
                  trip(TOP, End.REJOINED), outcome(0, 0, 0), List.of(reason), false))
          .as(reason.name())
          .isEqualTo(RotationVerdict.NEUTRAL);
    }
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 0), NONE, false))
        .isEqualTo(RotationVerdict.NEUTRAL);
  }

  @Test
  void verdict_aDeathBeatsEverything() {
    assertThat(
            RotationRules.verdict(
                trip(TOP, End.DIED), outcome(2, 1, 1), List.of(RotationReason.DIED), false))
        .isEqualTo(RotationVerdict.DIED);
  }

  @Test
  void worthReporting_plainWalkingIsNotReported() {
    assertThat(
            RotationRules.worthReporting(
                trip(TOP, End.REJOINED), outcome(0, 0, 0), RotationVerdict.NEUTRAL))
        .isFalse();
  }

  @Test
  void worthReporting_aFightAKillADeathOrAWastedTripIs() {
    assertThat(
            RotationRules.worthReporting(
                trip(TOP, End.REJOINED), outcome(0, 1, 0), RotationVerdict.NEUTRAL))
        .isTrue();
    assertThat(
            RotationRules.worthReporting(
                trip(TOP, End.REJOINED), outcome(1, 0, 0), RotationVerdict.GOOD))
        .isTrue();
    assertThat(
            RotationRules.worthReporting(
                trip(TOP, End.DIED), outcome(0, 0, 0), RotationVerdict.DIED))
        .isTrue();
    assertThat(
            RotationRules.worthReporting(
                trip(TOP, End.REJOINED), outcome(0, 0, 0), RotationVerdict.WASTED))
        .isTrue();
  }
}
