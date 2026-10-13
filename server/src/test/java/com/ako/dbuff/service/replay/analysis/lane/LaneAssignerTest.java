package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.dead;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inLane;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inRiver;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.jungleFarm;
import static org.assertj.core.api.Assertions.assertThat;

class LaneAssignerTest {
  private final LaneAssigner assigner = new LaneAssigner(new LaneProperties());

  @Test
  void laneOf_isTheLaneWithTheMostTimeInTheAssignmentSpan() {
    List<ActivitySegment> segments =
        List.of(
            inLane(-90, 60, Zone.Lane.TOP),
            inLane(60, 200, Zone.Lane.BOTTOM),
            inLane(200, 300, Zone.Lane.TOP));

    assertThat(assigner.laneOf(segments)).isEqualTo(Zone.Lane.BOTTOM);
  }

  @Test
  void laneOf_ignoresTimeAfterTheSpanAndTimeSpentDead() {
    List<ActivitySegment> segments =
        List.of(
            inLane(0, 100, Zone.Lane.MID),
            dead(100, 200),
            inLane(200, 240, Zone.Lane.TOP),
            inLane(240, 900, Zone.Lane.TOP));

    assertThat(assigner.laneOf(segments)).isEqualTo(Zone.Lane.MID);
  }

  @Test
  void laneOf_isNullForAHeroWhoMostlyFarmedTheJungle() {
    List<ActivitySegment> segments = List.of(inLane(0, 80, Zone.Lane.TOP), jungleFarm(80, 240));

    assertThat(assigner.laneOf(segments)).isNull();
  }

  @Test
  void laneOf_isNullWhenNoLaneTakesEnoughOfTheTime() {
    List<ActivitySegment> segments =
        List.of(inLane(0, 40, Zone.Lane.TOP), inRiver(40, 140), inLane(140, 200, Zone.Lane.BOTTOM));

    assertThat(assigner.laneOf(segments)).isNull();
  }

  @Test
  void assign_leavesAHeroWithoutALaneOutOfTheResult() {
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            0L, List.of(inLane(0, 240, Zone.Lane.BOTTOM)),
            1L, List.of(jungleFarm(0, 240)));

    assertThat(assigner.assign(segments)).containsOnlyKeys(0L).containsValue(Zone.Lane.BOTTOM);
  }
}
