package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.stayingAt;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.trace;
import static org.assertj.core.api.Assertions.assertThat;

class AwayTripsTest {
  private final AwayTrips finder = new AwayTrips(new TeamClusters(2500, 2, ZONES), ZONES, 20);

  /** Four heroes stand in mid; hero 4 is the one the test moves. */
  private static Map<Long, Trace> team(Trace rotating) {
    Map<Long, Trace> traces = new HashMap<>();
    for (long slot = 0; slot < 4; slot++) {
      traces.put(slot, stayingAt(0));
    }
    traces.put(4L, rotating);
    return traces;
  }

  private List<AwayTrip> find(Trace rotating) {
    return finder.find(new AwayTrips.Inputs(team(rotating), Map.of(), Map.of(), Map.of()));
  }

  @Test
  void find_aHeroWhoWalksToAnotherLaneAndBackHasATrip() {
    Trace hero = trace(i -> i >= 4 && i < 12 ? -4000 : 0, i -> 0);

    List<AwayTrip> trips = find(hero);

    assertThat(trips).hasSize(1);
    AwayTrip trip = trips.getFirst();
    assertThat(trip.slot()).isEqualTo(4L);
    assertThat(trip.startSeconds()).isEqualTo(20);
    assertThat(trip.endSeconds()).isEqualTo(60);
    assertThat(trip.from()).isEqualTo(MID);
    assertThat(trip.to()).isEqualTo(MID);
    assertThat(trip.groupZone()).isEqualTo(MID);
    assertThat(trip.end()).isEqualTo(End.REJOINED);
  }

  @Test
  void find_aHeroWhoJoinsTheGroupInAnotherLaneEndsThereAndTheGroupIsNotAway() {
    Trace hero = trace(i -> i >= 4 ? -4000 : 0, i -> 0);
    Map<Long, Trace> traces = team(hero);
    traces.put(0L, trace(i -> i >= 10 ? -4000 : 0, i -> 0));
    traces.put(1L, trace(i -> i >= 10 ? -4000 : 0, i -> 0));
    traces.put(2L, trace(i -> i >= 10 ? -4000 : 0, i -> 0));
    traces.put(3L, trace(i -> i >= 10 ? -4000 : 0, i -> 0));

    List<AwayTrip> trips = finder.find(new AwayTrips.Inputs(traces, Map.of(), Map.of(), Map.of()));

    assertThat(trips).hasSize(1);
    assertThat(trips.getFirst().to()).isEqualTo(TOP);
    assertThat(trips.getFirst().end()).isEqualTo(End.REJOINED);
    assertThat(trips.getFirst().startSeconds()).isEqualTo(20);
    assertThat(trips.getFirst().endSeconds()).isEqualTo(50);
  }

  @Test
  void find_aTripShorterThanTheThresholdIsDropped() {
    Trace hero = trace(i -> i >= 4 && i < 7 ? -4000 : 0, i -> 0);

    assertThat(find(hero)).isEmpty();
  }

  @Test
  void find_farmingTheJungleIsNotAway() {
    Trace hero = trace(i -> i >= 4 && i < 12 ? -4000 : 0, i -> 0);
    Map<Long, List<ActivitySegment>> activity =
        Map.of(
            4L,
            List.of(
                new ActivitySegment(0, 200, Activity.JUNGLE_FARM, Zone.Kind.JUNGLE, null, null)));

    List<AwayTrip> trips =
        finder.find(new AwayTrips.Inputs(team(hero), activity, Map.of(), Map.of()));

    assertThat(trips).isEmpty();
  }

  @Test
  void find_aDeathEndsTheTripAtTheMomentOfDeath() {
    Trace hero = trace(i -> -4000, i -> i >= 9 ? 1 : 0);

    List<AwayTrip> trips =
        finder.find(
            new AwayTrips.Inputs(team(hero), Map.of(), Map.of(4L, List.of(42.0)), Map.of()));

    assertThat(trips).hasSize(1);
    assertThat(trips.getFirst().end()).isEqualTo(End.DIED);
    assertThat(trips.getFirst().endSeconds()).isEqualTo(42);
    assertThat(trips.getFirst().to()).isEqualTo(TOP);
  }

  @Test
  void find_aBuybackWithNoDeadSampleEndsTheTripAsBoughtBack() {
    Trace hero = trace(i -> -4000, i -> 0);

    List<AwayTrip> trips =
        finder.find(
            new AwayTrips.Inputs(team(hero), Map.of(), Map.of(), Map.of(4L, List.of(60.0))));

    assertThat(trips.getFirst().end()).isEqualTo(End.BOUGHT_BACK);
    assertThat(trips.getFirst().endSeconds()).isEqualTo(60);
  }

  @Test
  void find_aTeleportEndingInsideTheTripMarksIt() {
    Trace hero = trace(i -> i >= 4 && i < 12 ? -4000 : 0, i -> 0);
    Map<Long, List<ActivitySegment>> activity =
        Map.of(
            4L,
            List.of(
                new ActivitySegment(
                    14, 26, Activity.TELEPORTING, Zone.Kind.BASE, null, ActivitySegment.Side.OWN)));

    List<AwayTrip> trips =
        finder.find(new AwayTrips.Inputs(team(hero), activity, Map.of(), Map.of()));

    assertThat(trips.getFirst().teleported()).isTrue();
  }

  @Test
  void find_aTripThatNeverEndsEndsWithTheGame() {
    Trace hero = trace(i -> i >= 4 ? -4000 : 0, i -> 0);

    List<AwayTrip> trips = find(hero);

    assertThat(trips.getFirst().end()).isEqualTo(End.GAME_END);
  }

  @Test
  void find_nobodyIsAwayWhenNoGroupExists() {
    Map<Long, Trace> traces = new HashMap<>();
    traces.put(0L, stayingAt(-4000));
    traces.put(1L, stayingAt(0));
    traces.put(2L, stayingAt(8000));

    assertThat(finder.find(new AwayTrips.Inputs(traces, Map.of(), Map.of(), Map.of()))).isEmpty();
  }

  @Test
  void find_aDeathKeepsAShortTripThatTheThresholdWouldDrop() {
    Trace hero = trace(i -> i >= 4 ? -4000 : 0, i -> i >= 6 ? 1 : 0);

    List<AwayTrip> trips =
        finder.find(
            new AwayTrips.Inputs(team(hero), Map.of(), Map.of(4L, List.of(28.0)), Map.of()));

    assertThat(trips).hasSize(1);
    assertThat(trips.getFirst().end()).isEqualTo(End.DIED);
    assertThat(trips.getFirst().endSeconds()).isEqualTo(28);
  }

  @Test
  void find_aTrackThatStopsEarlyIsNotADeath() {
    int[] xs = new int[10];
    java.util.Arrays.fill(xs, -4000);
    int[] ones = new int[10];
    java.util.Arrays.fill(ones, 1);
    Trace shortTrack = new Trace(0, 5, xs, new int[10], ones, ones);

    List<AwayTrip> trips =
        finder.find(new AwayTrips.Inputs(team(shortTrack), Map.of(), Map.of(), Map.of()));

    assertThat(trips).hasSize(1);
    assertThat(trips.getFirst().end()).isEqualTo(End.TRACK_ENDED);
  }

  @Test
  void find_aHeroWhoRespawnsInBaseAndWalksOutHasNoTrip() {
    Trace hero = trace(i -> i >= 12 ? -4000 : -9000, i -> i < 8 ? 1 : 0);

    List<AwayTrip> trips =
        finder.find(new AwayTrips.Inputs(team(hero), Map.of(), Map.of(4L, List.of(2.0)), Map.of()));

    assertThat(trips).isEmpty();
  }

  @Test
  void find_theArrivalIsWhenHeFirstStoodInTheDestinationLane() {
    Trace hero = trace(i -> i < 4 ? 0 : i < 8 ? 7000 : -4000, i -> 0);

    List<AwayTrip> trips = find(hero);

    assertThat(trips.getFirst().to()).isEqualTo(TOP);
    assertThat(trips.getFirst().arrivalSeconds()).isEqualTo(40);
  }
}
