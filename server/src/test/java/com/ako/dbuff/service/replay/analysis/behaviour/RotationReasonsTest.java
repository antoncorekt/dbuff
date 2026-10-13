package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.analysis.lane.MinuteSeries;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static org.assertj.core.api.Assertions.assertThat;

class RotationReasonsTest {
  private static final TripOutcome NOTHING = new TripOutcome(0, 0, 0, 0, null);

  private final BehaviourProperties properties = new BehaviourProperties();
  private final ReasonEvidence evidence =
      new ReasonEvidence(properties, new TripOutcomes(properties.getOutcomeLingerSeconds()));

  private static AwayTrip trip(End end) {
    return new AwayTrip(4L, 600, 620, 660, MID, TOP, MID, false, end);
  }

  private static MinuteSeries slowing() {
    Map<Integer, Long> totals = new HashMap<>();
    for (int minute = 0; minute <= 20; minute++) {
      totals.put(minute, minute <= 10 ? 500L * minute : 5000 + 100L * (minute - 10));
    }
    return new MinuteSeries(totals);
  }

  private static MidGameFacts mid(List<DamageHit> hits, List<MidGameFacts.TowerLoss> towers) {
    return new MidGameFacts(
        Map.of(4L, slowing()), Map.of(4L, slowing()), hits, Map.of(TeamSide.RADIANT, towers));
  }

  private List<RotationReason> reasons(
      AwayTrip trip,
      TripOutcome outcome,
      Optional<LaneProxy.Result> proxy,
      MidGameFacts mid,
      List<FightFact> fights) {
    return RotationReasons.of(facts(trip, outcome, proxy, mid, fights, Zone.Lane.MID));
  }

  private ReasonFacts facts(
      AwayTrip trip,
      TripOutcome outcome,
      Optional<LaneProxy.Result> proxy,
      MidGameFacts mid,
      List<FightFact> fights,
      Zone.Lane laneLeft) {
    return evidence.of(
        trip,
        TeamSide.RADIANT,
        laneLeft,
        outcome,
        proxy,
        mid,
        fights,
        new HeroPast(null, List.of(trip)));
  }

  @Test
  void of_aHeroWhoDidNothingWhileHisFarmDroppedHasBothReasons() {
    assertThat(
            reasons(
                trip(End.REJOINED),
                NOTHING,
                Optional.empty(),
                mid(List.of(), List.of()),
                List.of()))
        .containsExactly(RotationReason.DID_NOTHING, RotationReason.INCOME_DROP);
  }

  @Test
  void of_aHitOnAHeroOrATowerAtTheDestinationIsNotNothing() {
    for (boolean tower : new boolean[] {false, true}) {
      assertThat(
              reasons(
                  trip(End.REJOINED),
                  NOTHING,
                  Optional.empty(),
                  mid(List.of(new DamageHit(4L, 630, tower)), List.of()),
                  List.of()))
          .doesNotContain(RotationReason.DID_NOTHING);
    }
  }

  @Test
  void of_hitsBeforeArrivalOrOfAnotherHeroDoNotCount() {
    List<DamageHit> hits = List.of(new DamageHit(4L, 610, false), new DamageHit(3L, 630, false));

    assertThat(
            reasons(trip(End.REJOINED), NOTHING, Optional.empty(), mid(hits, List.of()), List.of()))
        .contains(RotationReason.DID_NOTHING);
  }

  @Test
  void of_aKillOrCreepsAreNotNothing() {
    assertThat(
            reasons(
                trip(End.REJOINED),
                new TripOutcome(1, 0, 0, 0, null),
                Optional.empty(),
                MidGameFacts.NONE,
                List.of()))
        .doesNotContain(RotationReason.DID_NOTHING);
    assertThat(
            reasons(
                trip(End.REJOINED),
                new TripOutcome(0, 3, 0, 0, null),
                Optional.empty(),
                MidGameFacts.NONE,
                List.of()))
        .doesNotContain(RotationReason.DID_NOTHING);
  }

  @Test
  void of_withoutTotalsThereIsNoIncomeDrop() {
    assertThat(reasons(trip(End.REJOINED), NOTHING, Optional.empty(), MidGameFacts.NONE, List.of()))
        .doesNotContain(RotationReason.INCOME_DROP);
  }

  @Test
  void of_aDeathOnTheTrip() {
    assertThat(reasons(trip(End.DIED), NOTHING, Optional.empty(), MidGameFacts.NONE, List.of()))
        .contains(RotationReason.DIED);
  }

  @Test
  void of_aFightInTheLaneThatEndedBeforeHeArrivedAndHeTookNoPartInOne() {
    FightFact over = new FightFact(1, 600, 615, "RADIANT", Set.of(0L), Zone.Lane.TOP);

    assertThat(
            reasons(
                trip(End.REJOINED), NOTHING, Optional.empty(), MidGameFacts.NONE, List.of(over)))
        .contains(RotationReason.ARRIVED_AFTER_FIGHT_ENDED);
  }

  @Test
  void of_aFightElsewhereOrAfterArrivalOrWithHimInItIsNotMissed() {
    List<FightFact> fights =
        List.of(
            new FightFact(1, 600, 615, "RADIANT", Set.of(0L), Zone.Lane.BOTTOM),
            new FightFact(2, 630, 650, "RADIANT", Set.of(0L), Zone.Lane.TOP),
            new FightFact(3, 500, 530, "RADIANT", Set.of(0L), Zone.Lane.TOP));

    assertThat(reasons(trip(End.REJOINED), NOTHING, Optional.empty(), MidGameFacts.NONE, fights))
        .doesNotContain(RotationReason.ARRIVED_AFTER_FIGHT_ENDED);
    assertThat(
            reasons(
                trip(End.REJOINED),
                new TripOutcome(0, 0, 1, 0, 4),
                Optional.empty(),
                MidGameFacts.NONE,
                List.of(new FightFact(4, 600, 615, "RADIANT", Set.of(0L), Zone.Lane.TOP))))
        .doesNotContain(RotationReason.ARRIVED_AFTER_FIGHT_ENDED);
  }

  @Test
  void of_aLaneLostOnTheCreepCount() {
    assertThat(
            reasons(
                trip(End.REJOINED),
                NOTHING,
                Optional.of(new LaneProxy.Result(1, 4)),
                MidGameFacts.NONE,
                List.of()))
        .contains(RotationReason.LEFT_LANE_UNDEFENDED);
    assertThat(
            reasons(
                trip(End.REJOINED),
                NOTHING,
                Optional.of(new LaneProxy.Result(4, 4)),
                MidGameFacts.NONE,
                List.of()))
        .doesNotContain(RotationReason.LEFT_LANE_UNDEFENDED);
  }

  @Test
  void of_anOwnTowerThatFellDuringTheTripNotBeforeOrAfter() {
    assertThat(
            reasons(
                trip(End.REJOINED),
                NOTHING,
                Optional.empty(),
                mid(List.of(), List.of(new MidGameFacts.TowerLoss(640.0, Zone.Lane.TOP))),
                List.of()))
        .contains(RotationReason.TOWER_LOST_WHILE_AWAY);
    assertThat(
            reasons(
                trip(End.REJOINED),
                NOTHING,
                Optional.empty(),
                mid(
                    List.of(),
                    List.of(
                        new MidGameFacts.TowerLoss(590.0, null),
                        new MidGameFacts.TowerLoss(661.0, null))),
                List.of()))
        .doesNotContain(RotationReason.TOWER_LOST_WHILE_AWAY);
  }

  @Test
  void of_standingInAFightAtTheDestinationIsNotDoingNothing() {
    assertThat(
            reasons(
                trip(End.REJOINED),
                new TripOutcome(0, 0, 1, 1, 4),
                Optional.empty(),
                MidGameFacts.NONE,
                List.of()))
        .doesNotContain(RotationReason.DID_NOTHING);
  }

  @Test
  void facts_aTowerCountsForTheVerdictOnlyInTheLaneHeLeft() {
    MidGameFacts mid = mid(List.of(), List.of(new MidGameFacts.TowerLoss(640.0, Zone.Lane.MID)));

    assertThat(facts(trip(End.REJOINED), NOTHING, Optional.empty(), mid, List.of(), Zone.Lane.MID))
        .extracting(ReasonFacts::towerInHisLane)
        .isEqualTo(true);
    assertThat(facts(trip(End.REJOINED), NOTHING, Optional.empty(), mid, List.of(), Zone.Lane.TOP))
        .extracting(ReasonFacts::towerInHisLane)
        .isEqualTo(false);
    assertThat(facts(trip(End.REJOINED), NOTHING, Optional.empty(), mid, List.of(), null))
        .extracting(ReasonFacts::towerInHisLane)
        .isEqualTo(false);
  }

  @Test
  void facts_noIncomeReadingWhenAnotherTripOfHisOverlapsTheStretchBefore() {
    AwayTrip earlier = new AwayTrip(4L, 520, 530, 560, MID, TOP, MID, false, End.REJOINED);
    AwayTrip trip = trip(End.REJOINED);
    ReasonFacts facts =
        evidence.of(
            trip,
            TeamSide.RADIANT,
            Zone.Lane.MID,
            NOTHING,
            Optional.empty(),
            mid(List.of(), List.of()),
            List.of(),
            new HeroPast(null, List.of(earlier, trip)));

    assertThat(facts.income()).isEmpty();
  }
}
