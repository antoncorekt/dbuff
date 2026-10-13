package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class LaneSwapsTest {
  private static final int SAMPLES = 150;
  private static final int TOP_X = -4000;
  private static final int MID_X = 0;
  private static final int BASE_X = -8000;
  private static final int WINDOW_START = 96;
  private static final int WINDOW_END = 144;

  private final LaneSwaps swaps = new LaneSwaps(ZONES, new BehaviourProperties());

  private static Trace trace(IntUnaryOperator x) {
    return BehaviourTestData.trace(SAMPLES, x, i -> 0);
  }

  private static LaneSlot assigned(long slot, Zone.Lane lane) {
    return new LaneSlot(slot, TeamSide.ofSlot(slot), lane, 0, 400);
  }

  @Test
  void find_aHeroWhoStoodMostlyInAnotherLaneIsASwap() {
    Map<Long, Trace> traces = Map.of(128L, trace(i -> i < 10 ? MID_X : TOP_X));

    assertThat(swaps.find(traces, List.of(assigned(128, Zone.Lane.MID))))
        .containsExactly(new LaneSwap(128, TeamSide.DIRE, Zone.Lane.MID, Zone.Lane.TOP, 480, 715));
  }

  @Test
  void find_theTimeIsWhenHeFirstAndLastStoodInTheNewLaneInsideTheWindow() {
    Map<Long, Trace> traces = Map.of(0L, trace(i -> i >= 108 ? TOP_X : MID_X));

    assertThat(swaps.find(traces, List.of(assigned(0, Zone.Lane.MID))))
        .containsExactly(new LaneSwap(0, TeamSide.RADIANT, Zone.Lane.MID, Zone.Lane.TOP, 540, 715));
  }

  @Test
  void find_aHeroInHisOwnLaneIsNotASwap() {
    assertThat(swaps.find(Map.of(0L, trace(i -> TOP_X)), List.of(assigned(0, Zone.Lane.TOP))))
        .isEmpty();
  }

  @Test
  void find_aHeroInTheBaseMostOfTheWindowHasNoLaneAndIsNotASwap() {
    assertThat(swaps.find(Map.of(0L, trace(i -> BASE_X)), List.of(assigned(0, Zone.Lane.MID))))
        .isEmpty();
  }

  @Test
  void find_aHeroWhoStoodInALaneForLessThanHalfTheWindowHasNoMainLane() {
    Map<Long, Trace> traces = Map.of(0L, trace(i -> i < WINDOW_START + 15 ? TOP_X : BASE_X));

    assertThat(swaps.find(traces, List.of(assigned(0, Zone.Lane.MID)))).isEmpty();
  }

  @Test
  void find_aHeroSplitEvenlyBetweenTwoLanesHasNoMainLane() {
    int middle = (WINDOW_START + WINDOW_END) / 2;
    Map<Long, Trace> traces = Map.of(0L, trace(i -> i < middle ? TOP_X : 9000));

    assertThat(swaps.find(traces, List.of(assigned(0, Zone.Lane.MID)))).isEmpty();
  }

  @Test
  void find_aHeroWhoIsDeadAllWindowIsNotASwap() {
    Trace dead = BehaviourTestData.trace(SAMPLES, i -> TOP_X, i -> 1);

    assertThat(swaps.find(Map.of(0L, dead), List.of(assigned(0, Zone.Lane.MID)))).isEmpty();
  }

  @Test
  void find_aHeroWithoutATrackIsSkipped() {
    assertThat(swaps.find(Map.of(), List.of(assigned(0, Zone.Lane.MID)))).isEmpty();
  }
}
