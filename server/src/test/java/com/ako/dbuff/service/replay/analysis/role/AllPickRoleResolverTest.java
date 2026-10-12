package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllPickRoleResolverTest {

  private final AllPickRoleResolver resolver =
      new AllPickRoleResolver(new ReplayAnalysisProperties());

  private static RolePlayer player(long slot, Long laneRole, long worth) {
    return new RolePlayer(slot, laneRole, worth, null, List.of());
  }

  private static Map<Long, Integer> roles(List<RoleAssignment> assignments) {
    return assignments.stream()
        .collect(Collectors.toMap(RoleAssignment::slot, RoleAssignment::role));
  }

  @Test
  void itsProfilesAreScoredWithThePlainWeightsInEveryStage() {
    assertThat(resolver.scoredAs(RoleStage.FINAL)).isEqualTo(RoleStage.INTENT);
  }

  @Test
  void supportsAllPickOnly() {
    assertThat(resolver.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(resolver.supports(TestModes.ABILITY_DRAFT)).isFalse();
  }

  @Test
  void aTypicalTeamGetsFiveDifferentRolesFromLanesAndFarm() {
    List<RolePlayer> team =
        List.of(
            player(0, 1L, 24000),
            player(1, 2L, 18000),
            player(2, 3L, 13000),
            player(3, 3L, 7000),
            player(4, 1L, 5000));

    List<RoleAssignment> result = resolver.resolve(team);

    assertThat(roles(result))
        .containsOnly(
            Map.entry(0L, 1),
            Map.entry(1L, 2),
            Map.entry(2L, 3),
            Map.entry(3L, 4),
            Map.entry(4L, 5));
    assertThat(result)
        .allSatisfy(
            a -> {
              assertThat(a.source()).isEqualTo(PlayerRoleSource.DERIVED);
              assertThat(a.confidence()).isBetween(0.0, 1.0);
              assertThat(a.evidence()).contains("lane role");
            });
    assertThat(result.getFirst().confidence()).as("no series halves it").isEqualTo(0.5);
  }

  @Test
  void aSupportInTheSafeLaneWithTheHighestFarmStillGetsOneRolePerTeam() {
    List<RolePlayer> team =
        List.of(
            player(128, 1L, 9000),
            player(129, 1L, 8800),
            player(130, 2L, 8500),
            player(131, 3L, 8000),
            player(132, 3L, 7000));

    List<RoleAssignment> result = resolver.resolve(team);

    assertThat(roles(result).values()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(roles(result).get(130L)).isEqualTo(2);
  }

  @Test
  void aJunglerWithLowFarmIsTheRoamingSupportAndTheHighestFarmingSafeLanerTheCarry() {
    List<RolePlayer> team =
        List.of(
            player(0, 1L, 20000),
            player(1, 2L, 15000),
            player(2, 3L, 12000),
            player(3, 4L, 6000),
            player(4, 1L, 4000));

    assertThat(roles(resolver.resolve(team)))
        .containsEntry(0L, 1)
        .containsEntry(3L, 4)
        .containsEntry(4L, 5);
  }

  @Test
  void jungleTimeInTheReplayMarksTheRoamingSupportWhenOpenDotaHasNoLaneRole() {
    ActivitySegment jungle =
        new ActivitySegment(0, 600, Activity.JUNGLE_FARM, Zone.Kind.JUNGLE, null, null);
    List<RolePlayer> team =
        List.of(
            player(0, null, 20000),
            player(1, null, 15000),
            player(2, null, 12000),
            new RolePlayer(3, null, 6000L, null, List.of(jungle)),
            player(4, null, 5000));

    assertThat(roles(resolver.resolve(team))).containsEntry(3L, 4);
  }

  @Test
  void noDataAtAllStillGivesEachRoleOnceWithZeroConfidence() {
    List<RolePlayer> team =
        List.of(
            new RolePlayer(0, null, null, null, List.of()),
            new RolePlayer(1, null, null, null, List.of()),
            new RolePlayer(2, null, null, null, List.of()),
            new RolePlayer(3, null, null, null, List.of()),
            new RolePlayer(4, null, null, null, List.of()));

    List<RoleAssignment> result = resolver.resolve(team);

    assertThat(roles(result).values()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(result).extracting(RoleAssignment::confidence).containsOnly(0.0);
  }

  @Test
  void equalNetWorthIsResolvedTheSameWayEveryTime() {
    List<RolePlayer> team =
        List.of(
            player(0, 1L, 10000),
            player(1, 1L, 10000),
            player(2, 3L, 10000),
            player(3, 3L, 10000),
            player(4, 2L, 10000));

    assertThat(resolver.resolve(team)).isEqualTo(resolver.resolve(team));
    assertThat(roles(resolver.resolve(team)).values()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
  }

  @Test
  void aTeamOfAnotherSizeIsNotResolved() {
    assertThat(resolver.resolve(List.of(player(0, 1L, 1000)))).isEmpty();
  }

  @Test
  void aPlayerWithoutASeriesIsNotRankedByFinalWorthAgainstLaningWorthOfTheOthers() {
    double[] flat = new double[13];
    List<RolePlayer> team =
        List.of(
            new RolePlayer(0, 1L, 20000L, series(13, 9000), List.of()),
            new RolePlayer(1, 2L, 15000L, series(13, 8000), List.of()),
            new RolePlayer(2, 3L, 12000L, series(13, 7000), List.of()),
            new RolePlayer(3, 3L, 6000L, series(13, 6000), List.of()),
            new RolePlayer(4, 1L, 5000L, null, List.of()));

    List<RoleAssignment> result = resolver.resolve(team);

    RoleAssignment noSeries = result.get(4);
    assertThat(noSeries.evidence()).contains("5.0 final and unknown in the laning phase");
    assertThat(noSeries.confidence()).isLessThanOrEqualTo(0.5);
    assertThat(result.get(0).evidence()).contains("1.0 final and 1.0 in the laning phase");
    assertThat(flat).hasSize(13);
  }

  @Test
  void aSeriesThatIsNotKnownYetAtTheLaningMinuteCountsAsUnknown() {
    double[] late = series(13, 5000);
    java.util.Arrays.fill(late, 0, 13, Double.NaN);
    RolePlayer player = new RolePlayer(0, 1L, 5000L, late, List.of());

    assertThat(player.worthAt(12)).isNull();
    assertThat(new RolePlayer(0, 1L, 5000L, null, List.of()).worthAt(12)).isNull();
    assertThat(new RolePlayer(0, 1L, 5000L, series(5, 7), List.of()).worthAt(12)).isEqualTo(7.0);
  }

  private static double[] series(int minutes, double value) {
    double[] series = new double[minutes];
    java.util.Arrays.fill(series, value);
    return series;
  }
}
