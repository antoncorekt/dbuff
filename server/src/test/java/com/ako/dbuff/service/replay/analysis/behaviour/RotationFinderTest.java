package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.stayingAt;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.trace;
import static org.assertj.core.api.Assertions.assertThat;

class RotationFinderTest {
  private final RotationFinder finder = new RotationFinder(earlyProperties(), ZONES);

  private static BehaviourProperties earlyProperties() {
    BehaviourProperties properties = new BehaviourProperties();
    properties.setRotationMinStartSeconds(0);
    return properties;
  }

  private static Map<Long, Trace> radiant(Trace hero) {
    Map<Long, Trace> traces = new HashMap<>();
    for (long slot = 0; slot < 4; slot++) {
      traces.put(slot, stayingAt(0));
    }
    traces.put(4L, hero);
    return traces;
  }

  private static RotationFinder.Inputs inputs(
      Trace hero, List<LaneSlot> lanes, TripOutcomes.Events events) {
    return new RotationFinder.Inputs(
        radiant(hero), Map.of(), Map.of(), Map.of(), lanes, events, MidGameFacts.NONE);
  }

  private static final Trace TO_TOP = trace(i -> i >= 4 ? -4000 : 0, i -> 0);

  @Test
  void find_aWalkToAnotherLaneWithAWonFightIsAGoodRotation() {
    TripOutcomes.Events events =
        new TripOutcomes.Events(
            Map.of(4L, List.of(62.0)),
            List.of(),
            List.of(new FightFact(7, 55, 70, "RADIANT", Set.of(4L), null)));

    List<Rotation> rotations = finder.find(inputs(TO_TOP, List.of(), events));

    assertThat(rotations).hasSize(1);
    Rotation r = rotations.getFirst();
    assertThat(r.team()).isEqualTo(TeamSide.RADIANT);
    assertThat(r.verdict()).isEqualTo(RotationVerdict.GOOD);
    assertThat(r.outcome().fightIndex()).isEqualTo(7);
    assertThat(r.laneLeft()).isNull();
  }

  @Test
  void find_aTripToAnotherLaneWithNothingComingOfItIsNotReported() {
    assertThat(
            finder.find(
                inputs(TO_TOP, List.of(), new TripOutcomes.Events(Map.of(), List.of(), List.of()))))
        .isEmpty();
  }

  @Test
  void find_aLaneLeftToTheEnemysCreepsMakesItAWastedRotation() {
    List<LaneSlot> lanes =
        List.of(
            new LaneSlot(4, TeamSide.RADIANT, Zone.Lane.MID, 0, 600),
            new LaneSlot(129, TeamSide.DIRE, Zone.Lane.MID, 0, 600));
    TripOutcomes.Events events =
        new TripOutcomes.Events(
            Map.of(), List.of(new CreepHit(129, 30, Zone.Lane.MID, false)), List.of());

    List<Rotation> rotations = finder.find(inputs(TO_TOP, lanes, events));

    assertThat(rotations).hasSize(1);
    assertThat(rotations.getFirst().verdict()).isEqualTo(RotationVerdict.WASTED);
    assertThat(rotations.getFirst().laneLeft()).isEqualTo(Zone.Lane.MID);
    assertThat(rotations.getFirst().proxy()).isEqualTo(Optional.of(new LaneProxy.Result(0, 1)));
  }

  @Test
  void find_aTripBackIntoTheHerosOwnLaneIsNotARotation() {
    List<LaneSlot> lanes = List.of(new LaneSlot(4, TeamSide.RADIANT, Zone.Lane.TOP, 0, 600));
    TripOutcomes.Events events =
        new TripOutcomes.Events(Map.of(4L, List.of(40.0)), List.of(), List.of());
    Trace stayingTop = trace(i -> -4000, i -> 0);

    assertThat(finder.find(inputs(stayingTop, lanes, events))).isEmpty();
  }

  @Test
  void find_theLaneCoveringTheTripStartIsTheHerosOwn() {
    List<LaneSlot> lanes = List.of(new LaneSlot(4, TeamSide.RADIANT, Zone.Lane.TOP, 0, 10));
    TripOutcomes.Events events =
        new TripOutcomes.Events(Map.of(4L, List.of(60.0)), List.of(), List.of());

    assertThat(finder.find(inputs(TO_TOP, lanes, events))).hasSize(1);
  }

  private List<Rotation> withTower(Zone.Lane towerLane) {
    MidGameFacts mid =
        new MidGameFacts(
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(TeamSide.RADIANT, List.of(new MidGameFacts.TowerLoss(50.0, towerLane))));
    List<LaneSlot> lanes = List.of(new LaneSlot(4, TeamSide.RADIANT, Zone.Lane.MID, 0, 600));
    return finder.find(
        new RotationFinder.Inputs(
            radiant(TO_TOP),
            Map.of(),
            Map.of(),
            Map.of(),
            lanes,
            new TripOutcomes.Events(Map.of(), List.of(), List.of()),
            mid));
  }

  @Test
  void find_aTowerOfTheLaneHeLeftFallingWhileAwayMakesItWasted() {
    List<Rotation> rotations = withTower(Zone.Lane.MID);

    assertThat(rotations).hasSize(1);
    assertThat(rotations.getFirst().verdict()).isEqualTo(RotationVerdict.WASTED);
    assertThat(rotations.getFirst().reasons())
        .containsExactly(RotationReason.DID_NOTHING, RotationReason.TOWER_LOST_WHILE_AWAY);
    assertThat(rotations.getFirst().income()).isEmpty();
  }

  @Test
  void find_aTowerOfAnotherLaneFallingDoesNotDecideAndNothingIsReported() {
    assertThat(withTower(Zone.Lane.BOTTOM)).isEmpty();
  }
}
