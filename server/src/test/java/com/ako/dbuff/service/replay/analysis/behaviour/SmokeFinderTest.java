package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class SmokeFinderTest {
  private static final SmokeOutcomes.Events NO_EVENTS =
      new SmokeOutcomes.Events(List.of(), Map.of(), List.of(), List.of());

  private final SmokeFinder finder = new SmokeFinder(new BehaviourProperties(), ZONES);

  private static SmokeHold hold(long slot, double add, Double remove) {
    return new SmokeHold(slot, null, add, remove, 45);
  }

  private static Map<Long, Trace> traces() {
    Trace walker = BehaviourTestData.trace(i -> i < 24 ? -4000 : 0, i -> 0);
    return Map.of(0L, walker, 1L, walker, 2L, BehaviourTestData.stayingAt(0), 128L, walker);
  }

  private SmokeFinder.Found find(
      List<SmokeEvents.Use> uses, List<SmokeHold> holds, SmokeOutcomes.Events events) {
    return finder.find(traces(), uses, holds, events);
  }

  @Test
  void find_theGroupIsTheUsersTeamHoldingTheModifierWithinTheGroupWindow() {
    SmokeFinder.Found found =
        find(
            List.of(new SmokeEvents.Use(0, 100)),
            List.of(
                hold(0, 100, 145.0),
                hold(1, 102.5, 140.0),
                hold(2, 110, 150.0),
                hold(128, 100, 145.0)),
            NO_EVENTS);

    assertThat(found.uses()).hasSize(1);
    SmokeUse use = found.uses().getFirst();
    assertThat(use.team()).isEqualTo(TeamSide.RADIANT);
    assertThat(use.userSlot()).isEqualTo(0);
    assertThat(use.group()).containsExactly(0L, 1L);
    assertThat(found.dropped()).isZero();
  }

  @Test
  void find_aModifierRemovedEarlyBreaksTheSmokeAtTheFirstBreak() {
    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 145.0), hold(1, 100, 130.0)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.end()).isEqualTo(SmokeUse.End.BROKEN);
    assertThat(use.startSeconds()).isEqualTo(100);
    assertThat(use.endSeconds()).isEqualTo(130);
  }

  @Test
  void find_modifiersRemovedAtTheirFullDurationExpireTheSmokeAtTheLastRemoval() {
    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 144.2), hold(1, 100, 145.0)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.end()).isEqualTo(SmokeUse.End.EXPIRED);
    assertThat(use.endSeconds()).isEqualTo(145);
  }

  @Test
  void find_aModifierWithNoRemovalLeavesTheEndReasonUnknown() {
    SmokeUse use =
        find(List.of(new SmokeEvents.Use(0, 100)), List.of(hold(0, 100, null)), NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.end()).isEqualTo(SmokeUse.End.UNKNOWN);
    assertThat(use.endSeconds()).isEqualTo(145);
  }

  @Test
  void find_aModifierWithoutADurationLeavesTheEndReasonUnknown() {
    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(new SmokeHold(0, null, 100, 120.0, 0)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.end()).isEqualTo(SmokeUse.End.UNKNOWN);
    assertThat(use.endSeconds()).isEqualTo(120);
  }

  @Test
  void find_aBrokenModifierDecidesEvenWhenAnotherIsUnknown() {
    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 130.0), hold(1, 100, null)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.end()).isEqualTo(SmokeUse.End.BROKEN);
  }

  @Test
  void find_readsTheZonesOfTheGroupAtTheStartAndAtTheEnd() {
    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 130.0), hold(1, 100, 135.0)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.startZone()).isEqualTo(TOP);
    assertThat(use.endZone()).isEqualTo(MID);
  }

  @Test
  void find_aUseWithoutAModifierOnTheUserWithinTheGroupWindowIsDropped() {
    SmokeFinder.Found found =
        find(
            List.of(new SmokeEvents.Use(0, 100), new SmokeEvents.Use(1, 150)),
            List.of(hold(1, 100, 120.0), hold(0, 90, 120.0)),
            NO_EVENTS);

    assertThat(found.uses()).isEmpty();
    assertThat(found.dropped()).isEqualTo(2);
  }

  @Test
  void find_judgesTheUseByTheWindowFromItsStartToTheOutcomeSecondsAfterItsEnd() {
    SmokeOutcomes.Events kill =
        new SmokeOutcomes.Events(
            List.of(new CombatSlots.HeroKill(128, 149.0, Set.of(1L))),
            Map.of(),
            List.of(),
            List.of());

    SmokeUse use =
        find(
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 130.0), hold(1, 100, 130.0)),
                kill)
            .uses()
            .getFirst();

    assertThat(use.result().outcome()).isEqualTo(SmokeOutcome.PAID_OFF);
    assertThat(use.result().kills()).isEqualTo(1);
  }

  @Test
  void find_aKillAfterTheWindowIsNotCounted() {
    SmokeOutcomes.Events kill =
        new SmokeOutcomes.Events(
            List.of(new CombatSlots.HeroKill(128, 151.0, Set.of(1L))),
            Map.of(),
            List.of(),
            List.of());

    SmokeUse use =
        find(List.of(new SmokeEvents.Use(0, 100)), List.of(hold(0, 100, 130.0)), kill)
            .uses()
            .getFirst();

    assertThat(use.result().outcome()).isEqualTo(SmokeOutcome.NOTHING);
  }

  @Test
  void find_aUseBeforeTheHornIsKeptWithNoZoneWhenNoTrackReachesBack() {
    SmokeFinder.Found found =
        find(List.of(new SmokeEvents.Use(0, -59)), List.of(hold(0, -59, -14.0)), NO_EVENTS);

    assertThat(found.uses()).hasSize(1);
    assertThat(found.uses().getFirst().startZone()).isNull();
  }

  @Test
  void find_aHeroSpawningAtTheUseIsReadOneSampleLater() {
    Trace spawner = BehaviourTestData.trace(i -> -4000, i -> i < 21 ? 1 : 0);

    SmokeUse use =
        finder
            .find(
                Map.of(0L, spawner),
                List.of(new SmokeEvents.Use(0, 100)),
                List.of(hold(0, 100, 130.0)),
                NO_EVENTS)
            .uses()
            .getFirst();

    assertThat(use.startZone()).isEqualTo(TOP);
  }

  @Test
  void find_aStackUsedTwiceByOneHeroIsOneSmoke() {
    SmokeFinder.Found found =
        find(
            List.of(new SmokeEvents.Use(0, 100), new SmokeEvents.Use(0, 101.5)),
            List.of(hold(0, 100, 130.0), hold(1, 100, 130.0)),
            NO_EVENTS);

    assertThat(found.uses()).hasSize(1);
    assertThat(found.dropped()).isZero();
  }

  @Test
  void find_twoGroupsOfOneTeamWithinTheGroupWindowStaySeparate() {
    SmokeFinder.Found found =
        find(
            List.of(new SmokeEvents.Use(0, 100), new SmokeEvents.Use(2, 101)),
            List.of(
                new SmokeHold(0, 0L, 100, 130.0, 45),
                new SmokeHold(1, 0L, 100, 130.0, 45),
                new SmokeHold(2, 2L, 101, 131.0, 45),
                new SmokeHold(3, 2L, 101, 131.0, 45)),
            NO_EVENTS);

    assertThat(found.uses())
        .extracting(SmokeUse::group)
        .containsExactly(List.of(0L, 1L), List.of(2L, 3L));
  }
}
