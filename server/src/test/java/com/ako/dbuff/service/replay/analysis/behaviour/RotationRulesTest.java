package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.BASE;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static org.assertj.core.api.Assertions.assertThat;

class RotationRulesTest {
  private static final Optional<LaneProxy.Result> NO_PROXY = Optional.empty();
  private static final Optional<LaneProxy.Result> LOST = Optional.of(new LaneProxy.Result(1, 4));
  private static final Optional<LaneProxy.Result> HELD = Optional.of(new LaneProxy.Result(4, 4));

  private static AwayTrip trip(Zone to, End end) {
    return new AwayTrip(0L, 600, 620, 650, MID, to, MID, false, end);
  }

  private static TripOutcome outcome(int kills, int fights, int won) {
    return new TripOutcome(kills, 0, fights, won, fights == 0 ? null : 3);
  }

  @Test
  void isRotation_needsAnotherLanesZone() {
    assertThat(RotationRules.isRotation(trip(TOP, End.REJOINED), Zone.Lane.MID)).isTrue();
    assertThat(RotationRules.isRotation(trip(TOP, End.REJOINED), null)).isTrue();
    assertThat(RotationRules.isRotation(trip(MID, End.REJOINED), Zone.Lane.MID)).isFalse();
    assertThat(RotationRules.isRotation(trip(BASE, End.REJOINED), Zone.Lane.MID)).isFalse();
  }

  @Test
  void isRotation_stayingInTheLaneHeLeftIsNotOne() {
    AwayTrip stayed = new AwayTrip(0L, 600, 620, 650, TOP, TOP, MID, false, End.REJOINED);

    assertThat(RotationRules.isRotation(stayed, null)).isFalse();
  }

  @Test
  void verdict_aKillIsGood() {
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(1, 0, 0), LOST))
        .isEqualTo(RotationVerdict.GOOD);
  }

  @Test
  void verdict_aWonFightIsGood() {
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 1), NO_PROXY))
        .isEqualTo(RotationVerdict.GOOD);
  }

  @Test
  void verdict_noKillAndALostLaneIsWasted() {
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 0, 0), LOST))
        .isEqualTo(RotationVerdict.WASTED);
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 0), LOST))
        .isEqualTo(RotationVerdict.WASTED);
  }

  @Test
  void verdict_aHeldOrUnknownLaneWithNothingElseIsNeutral() {
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 0), HELD))
        .isEqualTo(RotationVerdict.NEUTRAL);
    assertThat(RotationRules.verdict(trip(TOP, End.REJOINED), outcome(0, 1, 0), NO_PROXY))
        .isEqualTo(RotationVerdict.NEUTRAL);
  }

  @Test
  void verdict_aDeathBeatsEverything() {
    assertThat(RotationRules.verdict(trip(TOP, End.DIED), outcome(2, 1, 1), HELD))
        .isEqualTo(RotationVerdict.DIED);
  }

  @Test
  void worthReporting_plainWalkingIsNotReported() {
    assertThat(RotationRules.worthReporting(trip(TOP, End.REJOINED), outcome(0, 0, 0), HELD))
        .isFalse();
    assertThat(RotationRules.worthReporting(trip(TOP, End.REJOINED), outcome(0, 0, 0), NO_PROXY))
        .isFalse();
  }

  @Test
  void worthReporting_aFightAKillADeathOrALostLaneIs() {
    assertThat(RotationRules.worthReporting(trip(TOP, End.REJOINED), outcome(0, 1, 0), NO_PROXY))
        .isTrue();
    assertThat(RotationRules.worthReporting(trip(TOP, End.REJOINED), outcome(1, 0, 0), NO_PROXY))
        .isTrue();
    assertThat(RotationRules.worthReporting(trip(TOP, End.DIED), outcome(0, 0, 0), NO_PROXY))
        .isTrue();
    assertThat(RotationRules.worthReporting(trip(TOP, End.REJOINED), outcome(0, 0, 0), LOST))
        .isTrue();
  }
}
