package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.dead;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inLane;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inRiver;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.jungleFarm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaneShadowAssignerTest {
  private static final long CARRY = 4;
  private static final long SUPPORT = 3;
  private static final int STEP = 10;
  private static final int SAMPLES = 60;

  private final LaneShadowAssigner assigner = new LaneShadowAssigner(new LaneProperties());
  private final LaneHero carry =
      new LaneHero(
          CARRY, TeamSide.RADIANT, Zone.Lane.BOTTOM, new LaningWindow(0, 600, LaneEnd.TOWER_FELL));

  private static ReplayPlayerTrackDomain trackAt(int x) {
    return trackAt(x, 0);
  }

  private static ReplayPlayerTrackDomain trackAt(int x, int nearFromSample) {
    int[] xs = new int[SAMPLES];
    Arrays.fill(xs, x);
    for (int i = 0; i < nearFromSample; i++) {
      xs[i] = x + 20000;
    }
    return ReplayPlayerTrackDomain.builder()
        .startSeconds(0)
        .stepSeconds(STEP)
        .x(xs)
        .y(new int[SAMPLES])
        .build();
  }

  private Map<Long, Zone.Lane> assign(
      List<ActivitySegment> supportSegments, ReplayPlayerTrackDomain supportTrack) {
    return assigner.assign(
        Map.of(SUPPORT, supportSegments),
        Map.of(SUPPORT, TeamSide.RADIANT),
        Map.of(CARRY, trackAt(0), SUPPORT, supportTrack),
        List.of(carry));
  }

  @Test
  void assign_givesASupportWhoStoodNextToTheCarryTheCarrysLane() {
    var lanes = assign(List.of(inLane(0, 600, Zone.Lane.BOTTOM)), trackAt(800));

    assertThat(lanes).containsEntry(SUPPORT, Zone.Lane.BOTTOM);
  }

  @Test
  void assign_countsTimeNextToTheCarryEvenWhenTheZoneIsNotTheLane() {
    var lanes = assign(List.of(inRiver(0, 600)), trackAt(800));

    assertThat(lanes).containsEntry(SUPPORT, Zone.Lane.BOTTOM);
  }

  @Test
  void assign_givesTheLaneOfTheLaterMinutesToAHeroWhoStartedElsewhere() {
    var lanes =
        assign(
            List.of(inLane(0, 200, Zone.Lane.TOP), inLane(200, 600, Zone.Lane.BOTTOM)),
            trackAt(800, 20));

    assertThat(lanes).containsEntry(SUPPORT, Zone.Lane.BOTTOM);
  }

  @Test
  void assign_leavesARoamerWithoutALane() {
    var lanes = assign(List.of(inRiver(0, 600)), trackAt(9000));

    assertThat(lanes).isEmpty();
  }

  @Test
  void assign_leavesAJunglerWithoutALaneEvenWhenHeFarmsNextToTheCarry() {
    var lanes = assign(List.of(jungleFarm(0, 600)), trackAt(1200));

    assertThat(lanes).isEmpty();
  }

  @Test
  void assign_leavesAHeroWithoutALaneWhenHeWasNextToTheCarryTooShortAWhile() {
    var lanes = assign(List.of(inRiver(0, 600)), trackAt(800, 55));

    assertThat(lanes).isEmpty();
  }

  @Test
  void assign_doesNotCountTimeDead() {
    var lanes = assign(List.of(dead(0, 570), inLane(570, 600, Zone.Lane.BOTTOM)), trackAt(800));

    assertThat(lanes).isEmpty();
  }

  @Test
  void assign_ignoresHeroesWhoAlreadyHaveALaneAndTeammatesOfTheOtherSide() {
    var withLane =
        assigner.assign(
            Map.of(CARRY, List.of(inLane(0, 600, Zone.Lane.BOTTOM))),
            Map.of(CARRY, TeamSide.RADIANT),
            Map.of(CARRY, trackAt(0)),
            List.of(carry));
    var enemy =
        assigner.assign(
            Map.of(SUPPORT, List.of(inLane(0, 600, Zone.Lane.BOTTOM))),
            Map.of(SUPPORT, TeamSide.DIRE),
            Map.of(CARRY, trackAt(0), SUPPORT, trackAt(800)),
            List.of(carry));

    assertThat(withLane).isEmpty();
    assertThat(enemy).isEmpty();
  }

  @Test
  void validate_rejectsValuesThatCannotWork() {
    assertThatCode(new LaneProperties()::validate).doesNotThrowAnyException();
    for (String name : List.of("shadow-distance", "shadow-min-share", "shadow-min-seconds")) {
      LaneProperties p = new LaneProperties();
      switch (name) {
        case "shadow-distance" -> p.setShadowDistance(0);
        case "shadow-min-share" -> p.setShadowMinShare(1.5);
        default -> p.setShadowMinSeconds(0);
      }
      assertThatThrownBy(p::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("replay.analysis.lanes." + name);
    }
  }
}
