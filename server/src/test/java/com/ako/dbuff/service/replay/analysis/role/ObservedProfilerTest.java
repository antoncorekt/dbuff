package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

  private static RolePlayer produced(long slot, PlayerOutput output) {
    return new RolePlayer(slot, 1L, 10000L, null, List.of(), null, List.of(), output);
  }

  private static List<RolePlayer> team(PlayerOutput... outputs) {
    return java.util.stream.IntStream.range(0, outputs.length)
        .mapToObj(i -> produced(i, outputs[i]))
        .toList();
  }

  @Test
  void damageHealingVisionAndDurabilityAreTheShareOfTheTeamOverTheFullShare() {
    List<FunctionProfile> profiles =
        profiler.profile(
            team(
                new PlayerOutput(35L, 0L, 70L, 0L, 0L),
                new PlayerOutput(35L, 50L, 0L, 0L, 50L),
                new PlayerOutput(10L, 0L, 0L, 0L, 0L),
                new PlayerOutput(10L, 0L, 0L, 0L, 0L),
                new PlayerOutput(10L, 0L, 0L, 0L, 0L)));

    assertThat(profiles)
        .extracting(FunctionProfile::damage)
        .containsExactly(1.0, 1.0, 0.1 / 0.35, 0.1 / 0.35, 0.1 / 0.35);
    assertThat(profiles)
        .extracting(FunctionProfile::durability)
        .containsExactly(1.0, 0.0, 0.0, 0.0, 0.0);
    assertThat(profiles)
        .extracting(FunctionProfile::saveHeal)
        .containsExactly(0.0, 1.0, 0.0, 0.0, 0.0);
    assertThat(profiles)
        .extracting(FunctionProfile::visionDuty)
        .containsExactly(0.0, 1.0, 0.0, 0.0, 0.0);
  }

  @Test
  void aShareAboveTheFullShareIsCutAtOne() {
    List<FunctionProfile> profiles =
        profiler.profile(
            team(
                new PlayerOutput(90L, null, null, null, null),
                new PlayerOutput(10L, null, null, null, null),
                new PlayerOutput(0L, null, null, null, null),
                new PlayerOutput(0L, null, null, null, null),
                new PlayerOutput(0L, null, null, null, null)));

    assertThat(profiles.get(0).damage()).isEqualTo(1.0);
    assertThat(profiles.get(0).saveHeal()).isNull();
  }

  @Test
  void aStatIsUnknownForTheWholeTeamWhenAnyPlayerHasNoStoredValue() {
    List<FunctionProfile> missing =
        profiler.profile(
            team(
                new PlayerOutput(10L, null, null, null, null),
                PlayerOutput.NONE,
                new PlayerOutput(10L, null, null, null, null),
                new PlayerOutput(10L, null, null, null, null),
                new PlayerOutput(10L, null, null, null, null)));

    assertThat(missing).extracting(FunctionProfile::damage).containsOnlyNulls();
  }

  @Test
  void aTeamTotalOfZeroGivesEveryoneZero() {
    PlayerOutput none = new PlayerOutput(0L, 0L, 0L, 0L, 0L);
    List<FunctionProfile> silent = profiler.profile(team(none, none, none, none, none));

    assertThat(silent).extracting(FunctionProfile::damage).containsOnly(0.0);
    assertThat(silent).extracting(FunctionProfile::saveHeal).containsOnly(0.0);
  }

  @Test
  void farmPriorityKeepsTheLastHitsWhenNoNetWorthIsStored() {
    PlayerOutput leader = new PlayerOutput(null, null, null, 70L, null);
    PlayerOutput other = new PlayerOutput(null, null, null, 7L, null);
    List<RolePlayer> noWorth =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i ->
                    new RolePlayer(
                        i, 1L, null, null, List.of(), null, List.of(), i == 0 ? leader : other))
            .toList();

    List<FunctionProfile> profiles = profiler.profile(noWorth);

    assertThat(profiles.get(0).farmPriority()).isEqualTo(1.0);
    assertThat(profiles.get(1).farmPriority()).isBetween(0.0, 1.0);
  }

  @Test
  void aTeamOfOneHasNoNetWorthPlaceToCompare() {
    List<FunctionProfile> profiles =
        profiler.profile(List.of(new RolePlayer(0, 1L, 9000L, null, List.of())));

    assertThat(profiles.get(0).farmPriority()).isNull();
  }

  @Test
  void lastHitsPullFarmPriorityTowardsTheShareOfTheTeam() {
    List<RolePlayer> byWorth = team(player(3, 3L, 7000));
    List<RolePlayer> withLastHits =
        java.util.stream.IntStream.range(0, byWorth.size())
            .mapToObj(
                i -> {
                  RolePlayer p = byWorth.get(i);
                  long lastHits = i == 4 ? 700 : 0;
                  return new RolePlayer(
                      p.slot(),
                      p.laneRole(),
                      p.finalNetWorth(),
                      null,
                      List.of(),
                      null,
                      List.of(),
                      new PlayerOutput(null, null, null, lastHits, null));
                })
            .toList();

    assertThat(profiler.profile(byWorth).get(4).farmPriority()).isZero();
    assertThat(profiler.profile(withLastHits).get(4).farmPriority()).isCloseTo(0.3, within(1e-9));
    assertThat(profiler.profile(withLastHits).get(0).farmPriority()).isCloseTo(0.7, within(1e-9));
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
