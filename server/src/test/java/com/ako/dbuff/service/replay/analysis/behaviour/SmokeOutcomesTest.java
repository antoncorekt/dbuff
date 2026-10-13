package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeOutcomesTest {
  private static final List<Long> GROUP = List.of(0L, 1L);

  private static SmokeResult judge(SmokeOutcomes.Events events) {
    return SmokeOutcomes.of(TeamSide.RADIANT, GROUP, 100, 200, events);
  }

  private static SmokeOutcomes.Events events(
      Map<Long, List<Double>> kills,
      Map<Long, List<Double>> deaths,
      List<TowerFall> buildings,
      Map<TeamSide, List<Double>> roshan) {
    return new SmokeOutcomes.Events(kills, deaths, buildings, roshan);
  }

  @Test
  void of_aKillByAGroupMemberPaidOffAndAKillWithTwoTakersIsOne() {
    SmokeResult result =
        judge(
            events(
                Map.of(0L, List.of(150.0), 1L, List.of(150.0)),
                Map.of(128L, List.of(150.0)),
                List.of(),
                Map.of()));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(result.kills()).isEqualTo(1);
    assertThat(result.enemyDeaths()).isEqualTo(1);
  }

  @Test
  void of_aKillByAHeroOutsideTheGroupIsNotTheSmokes() {
    SmokeResult result = judge(events(Map.of(2L, List.of(150.0)), Map.of(), List.of(), Map.of()));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_anEnemyTowerOrBarracksPaidOffButAnOwnOrDeniedOneDoesNot() {
    TowerFall enemyTower = new TowerFall(TeamSide.DIRE, 150, Zone.Lane.TOP, false);
    TowerFall ownTower = new TowerFall(TeamSide.RADIANT, 150, Zone.Lane.TOP, false);
    TowerFall denied = new TowerFall(TeamSide.DIRE, 150, Zone.Lane.TOP, true);

    assertThat(judge(events(Map.of(), Map.of(), List.of(enemyTower), Map.of())).outcome())
        .isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(judge(events(Map.of(), Map.of(), List.of(ownTower, denied), Map.of())).outcome())
        .isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_roshanKilledByTheTeamPaidOffButNotByTheEnemy() {
    assertThat(
            judge(events(Map.of(), Map.of(), List.of(), Map.of(TeamSide.RADIANT, List.of(120.0))))
                .outcome())
        .isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(
            judge(events(Map.of(), Map.of(), List.of(), Map.of(TeamSide.DIRE, List.of(120.0))))
                .outcome())
        .isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_aGroupHeroDyingWithNoEnemyDeathBackfired() {
    SmokeResult result = judge(events(Map.of(), Map.of(0L, List.of(110.0)), List.of(), Map.of()));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.BACKFIRED);
    assertThat(result.groupDeaths()).isEqualTo(1);
  }

  @Test
  void of_aGroupHeroDyingWhileAnEnemyDiedToSomeoneElseIsNothing() {
    SmokeResult result =
        judge(
            events(
                Map.of(), Map.of(0L, List.of(110.0), 129L, List.of(120.0)), List.of(), Map.of()));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_aKillBeatsTheDeathsOfTheGroup() {
    SmokeResult result =
        judge(
            events(
                Map.of(1L, List.of(130.0)),
                Map.of(0L, List.of(110.0), 128L, List.of(130.0)),
                List.of(),
                Map.of()));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.PAID_OFF);
  }

  @Test
  void of_nothingHappenedIsNothingAndEventsOutsideTheWindowAreIgnored() {
    SmokeResult result =
        judge(
            events(
                Map.of(0L, List.of(99.0)),
                Map.of(1L, List.of(201.0)),
                List.of(new TowerFall(TeamSide.DIRE, 250, null, false)),
                Map.of()));

    assertThat(result).isEqualTo(new SmokeResult(SmokeOutcome.NOTHING, 0, 0, 0, 0, 0));
  }
}
