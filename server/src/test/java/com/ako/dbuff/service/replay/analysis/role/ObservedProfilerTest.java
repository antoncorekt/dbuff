package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedProfilerTest {

  private final ObservedProfiler profiler =
      new ObservedProfiler(new ReplayAnalysisProperties(), new RoleProfileProperties());

  private static RolePlayer player(long slot, Long laneRole, long worth) {
    return new RolePlayer(slot, laneRole, worth, null, List.of());
  }

  private static RolePlayer withActivity(
      long slot, Long laneRole, long worth, ActivitySegment... segments) {
    return new RolePlayer(slot, laneRole, worth, null, List.of(segments));
  }

  private List<RolePlayer> team(RolePlayer fourth) {
    return List.of(
        player(0, 1L, 24000),
        player(1, 2L, 18000),
        player(2, 3L, 13000),
        fourth,
        player(4, 1L, 5000));
  }

  @Test
  void theStageIsFinal() {
    assertThat(profiler.stage()).isEqualTo(RoleStage.FINAL);
  }

  @Test
  void farmPriorityFallsFromTheRichestToThePoorestOfTheTeam() {
    List<FunctionProfile> profiles = profiler.profile(team(player(3, 3L, 7000)));

    assertThat(profiles)
        .extracting(FunctionProfile::farmPriority)
        .containsExactly(1.0, 0.75, 0.5, 0.25, 0.0);
  }

  @Test
  void theLaneComesFromTheLaneRoleAndTheMidLaneFromTheReplay() {
    ActivitySegment mid =
        new ActivitySegment(0, 600, Activity.LANE_FARM, Zone.Kind.LANE, Zone.Lane.MID, null);

    List<FunctionProfile> profiles = profiler.profile(team(withActivity(3, 3L, 7000, mid)));

    assertThat(profiles)
        .extracting(FunctionProfile::lane)
        .containsExactly(
            LanePosition.SAFE,
            LanePosition.MID,
            LanePosition.OFF,
            LanePosition.MID,
            LanePosition.SAFE);
  }

  @Test
  void jungleAndRoamingComeFromTheLaningActivity() {
    ActivitySegment jungle =
        new ActivitySegment(0, 600, Activity.JUNGLE_FARM, Zone.Kind.JUNGLE, null, null);

    FunctionProfile junglerProfile =
        profiler.profile(team(withActivity(3, null, 7000, jungle))).get(3);

    assertThat(junglerProfile.jungle()).isEqualTo(1.0);
    assertThat(junglerProfile.roaming()).isZero();
    assertThat(junglerProfile.lane()).isNull();
  }

  @Test
  void aJungleLaneRoleWithoutActivityStillSaysJungleAndRoamingStaysUnknown() {
    FunctionProfile profile = profiler.profile(team(player(3, 4L, 7000))).get(3);

    assertThat(profile.jungle()).isEqualTo(0.7);
    assertThat(profile.roaming()).isNull();
  }

  @Test
  void whatHasNoStoredSourceStaysUnknown() {
    for (FunctionProfile p : profiler.profile(team(player(3, 3L, 7000)))) {
      assertThat(p.visionDuty()).isNull();
      assertThat(p.initiation()).isNull();
      assertThat(p.disable()).isNull();
      assertThat(p.saveHeal()).isNull();
      assertThat(p.damage()).isNull();
      assertThat(p.durability()).isNull();
    }
  }

  @Test
  void aPlayerWithNothingStoredIsUnknownInEverything() {
    List<RolePlayer> team =
        java.util.stream.LongStream.range(0, 5)
            .mapToObj(s -> new RolePlayer(s, null, null, null, List.of()))
            .toList();

    assertThat(profiler.profile(team)).containsOnly(FunctionProfile.UNKNOWN);
  }
}
