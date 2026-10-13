package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class SplitFinderTest {
  private static final int SAMPLES = 241;
  private static final int SPLIT_AT = 180;

  private final SplitFinder finder = new SplitFinder(new BehaviourProperties(), ZONES);

  private static Map<Long, Trace> traces() {
    Map<Long, Trace> traces = new HashMap<>();
    for (long slot : List.of(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L)) {
      traces.put(slot, BehaviourTestData.trace(SAMPLES, i -> 0, i -> 0));
    }
    traces.put(131L, BehaviourTestData.trace(SAMPLES, i -> i < SPLIT_AT ? 0 : -5000, i -> 0));
    traces.put(132L, BehaviourTestData.trace(SAMPLES, i -> i < SPLIT_AT ? 0 : 9000, i -> 0));
    return traces;
  }

  private static Map<TeamSide, TeamEvents> events() {
    return Map.of(
        TeamSide.RADIANT,
        new TeamEvents(List.of(), List.of(1000.0, 1100.0), List.of(), List.of(950.0)),
        TeamSide.DIRE,
        new TeamEvents(
            List.of(1000.0, 1100.0, 800.0), List.of(), List.of(950.0, 100.0), List.of()));
  }

  @Test
  void find_labelsEachTeamAndCountsWhatHappenedInsideEachStretch() {
    List<TeamSplit> found = finder.find(new SplitFinder.Inputs(traces(), Map.of(), 720, events()));

    assertThat(found)
        .containsExactly(
            new TeamSplit(TeamSide.RADIANT, new Stretch(720, 1200, Posture.GROUPED), 0, 2, 0, 1),
            new TeamSplit(TeamSide.DIRE, new Stretch(720, 900, Posture.GROUPED), 1, 0, 0, 0),
            new TeamSplit(TeamSide.DIRE, new Stretch(900, 1200, Posture.SPLIT), 2, 0, 1, 0));
  }

  @Test
  void find_readsNothingBeforeTheStartSecond() {
    List<TeamSplit> found = finder.find(new SplitFinder.Inputs(traces(), Map.of(), 1000, events()));

    assertThat(found)
        .allSatisfy(s -> assertThat(s.stretch().startSeconds()).isGreaterThanOrEqualTo(1020));
  }
}
