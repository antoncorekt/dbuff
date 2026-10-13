package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.CombatSlots.HeroKill;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeEvents.RoshanKill;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeOutcomesTest {
  private static final List<Long> GROUP = List.of(0L, 1L);
  private static final SmokeOutcomes.Events EMPTY =
      new SmokeOutcomes.Events(List.of(), Map.of(), List.of(), List.of());

  private static SmokeResult judge(SmokeOutcomes.Events events) {
    return SmokeOutcomes.of(TeamSide.RADIANT, GROUP, 100, 200, events);
  }

  private static SmokeOutcomes.Events kills(HeroKill... kills) {
    return new SmokeOutcomes.Events(List.of(kills), Map.of(), List.of(), List.of());
  }

  private static SmokeOutcomes.Events deaths(Map<Long, List<Double>> deaths) {
    return new SmokeOutcomes.Events(List.of(), deaths, List.of(), List.of());
  }

  private static SmokeOutcomes.Events buildings(TowerFall... buildings) {
    return new SmokeOutcomes.Events(List.of(), Map.of(), List.of(buildings), List.of());
  }

  private static TowerFall fall(TeamSide owner, boolean denied, Long... attackers) {
    return new TowerFall(owner, 150, Zone.Lane.TOP, denied, Set.of(attackers));
  }

  @Test
  void of_aKillByAGroupMemberPaidOffAndTwoDeathsInOneSecondAreTwoKills() {
    SmokeResult result =
        judge(kills(new HeroKill(128, 150, Set.of(0L, 1L)), new HeroKill(129, 150, Set.of(1L))));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(result.kills()).isEqualTo(2);
  }

  @Test
  void of_aKillByAHeroOutsideTheGroupIsNotTheSmokes() {
    assertThat(judge(kills(new HeroKill(128, 150, Set.of(2L)))).outcome())
        .isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_anEnemyBuildingTakenWithAGroupMemberAmongTheAttackersPaidOff() {
    assertThat(judge(buildings(fall(TeamSide.DIRE, false, 1L, 2L))).outcome())
        .isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(judge(buildings(fall(TeamSide.DIRE, false, 1L))).buildings()).isEqualTo(1);
  }

  @Test
  void of_aBuildingTakenByTeammatesOutsideTheGroupOwnOrDeniedDoesNotCount() {
    assertThat(
            judge(
                    buildings(
                        fall(TeamSide.DIRE, false, 2L, 3L),
                        fall(TeamSide.DIRE, false),
                        fall(TeamSide.RADIANT, false, 0L),
                        fall(TeamSide.DIRE, true, 0L)))
                .outcome())
        .isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_roshanKilledByAGroupMemberPaidOffButNotByAnotherHero() {
    assertThat(
            judge(
                    new SmokeOutcomes.Events(
                        List.of(), Map.of(), List.of(), List.of(new RoshanKill(1, 120))))
                .outcome())
        .isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(
            judge(
                    new SmokeOutcomes.Events(
                        List.of(), Map.of(), List.of(), List.of(new RoshanKill(3, 120))))
                .outcome())
        .isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void of_aGroupHeroDyingWithoutAGroupKillBackfired() {
    SmokeResult result = judge(deaths(Map.of(0L, List.of(110.0))));

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.BACKFIRED);
    assertThat(result.groupDeaths()).isEqualTo(1);
  }

  @Test
  void of_anEnemyDyingToATeammateDoesNotHideABackfire() {
    SmokeOutcomes.Events events =
        new SmokeOutcomes.Events(
            List.of(new HeroKill(129, 120, Set.of(3L))),
            Map.of(0L, List.of(110.0), 129L, List.of(120.0)),
            List.of(),
            List.of());

    SmokeResult result = judge(events);

    assertThat(result.outcome()).isEqualTo(SmokeOutcome.BACKFIRED);
    assertThat(result.enemyDeaths()).isEqualTo(1);
  }

  @Test
  void of_aGroupKillBeatsTheDeathsOfTheGroup() {
    SmokeOutcomes.Events events =
        new SmokeOutcomes.Events(
            List.of(new HeroKill(128, 130, Set.of(1L))),
            Map.of(0L, List.of(110.0)),
            List.of(),
            List.of());

    assertThat(judge(events).outcome()).isEqualTo(SmokeOutcome.PAID_OFF);
  }

  @Test
  void of_eventsOutsideTheWindowAreIgnored() {
    SmokeOutcomes.Events events =
        new SmokeOutcomes.Events(
            List.of(new HeroKill(128, 99, Set.of(0L))),
            Map.of(1L, List.of(201.0)),
            List.of(new TowerFall(TeamSide.DIRE, 250, null, false, Set.of(0L))),
            List.of());

    assertThat(judge(events)).isEqualTo(new SmokeResult(SmokeOutcome.NOTHING, 0, 0, 0, 0, 0));
    assertThat(judge(EMPTY).outcome()).isEqualTo(SmokeOutcome.NOTHING);
  }
}
