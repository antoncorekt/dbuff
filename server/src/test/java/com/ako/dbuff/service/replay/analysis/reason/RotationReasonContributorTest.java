package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RotationReasonContributorTest {
  private final RotationReasonContributor contributor =
      new RotationReasonContributor(
          new ReasonProperties(),
          new ModeTestKits(Map.of(3L, ModeTestKits.hero(3, "Windranger")), Map.of()));

  private static TeamRotationDomain trip(
      String team, long slot, int start, int end, String verdict, String reasons) {
    return TeamRotationDomain.builder()
        .team(team)
        .playerSlot(slot)
        .startSeconds(start)
        .endSeconds(end)
        .fromZone("LANE")
        .fromLane("TOP")
        .toZone("LANE")
        .toLane("BOTTOM")
        .verdict(verdict)
        .reasons(reasons)
        .build();
  }

  private static FightPlayerDomain presence(long slot, String presence) {
    return FightPlayerDomain.builder().playerSlot(slot).presence(presence).build();
  }

  private static LateFightContext context(
      List<TeamRotationDomain> trips, FightPlayerDomain... players) {
    FightContext fight =
        new FightContext(
            1,
            TestModes.ALL_PICK,
            FightDomain.builder()
                .fightIndex(0)
                .startSeconds(1000.0)
                .endSeconds(1030.0)
                .winner("DIRE")
                .build(),
            List.of(players),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(1L, 3L),
            List.of());
    return new LateFightContext(fight, List.of(), trips);
  }

  @Test
  void aWastedTripThatEndedInTheWindowByAHeroWhoMissedTheFightIsAPlayerReason() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                List.of(trip("RADIANT", 1, 900, 970, "WASTED", "INCOME_DROP")),
                presence(1, "ABSENT_FAR")));

    assertThat(reasons)
        .extracting(
            Reason::code, Reason::scope, Reason::subjectSlot, Reason::source, Reason::severity)
        .containsExactly(
            tuple("ROTATION_WASTED_BEFORE_FIGHT", ReasonScope.PLAYER, 1L, "ROTATION", 0.4));
    assertThat(reasons.get(0).evidence())
        .isEqualTo(
            "Windranger left top lane for bottom lane at 15:00; the trip ended 30 s before the"
                + " fight and gave nothing (farm dropped)");
  }

  @Test
  void aTripThatDiedIsMoreSevere_andAnOverlappingTripStillCounts() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                List.of(trip("RADIANT", 1, 980, 1010, "DIED", null)), presence(1, "ABSENT_DEAD")));

    assertThat(reasons).extracting(Reason::severity).containsExactly(0.5);
    assertThat(reasons.get(0).evidence())
        .isEqualTo(
            "Windranger left top lane for bottom lane at 16:20; the trip was still away when the"
                + " fight began and he died on it");
  }

  @Test
  void aTripThatEndedBeforeTheWindowGivesNothing() {
    assertThat(
            contributor.contribute(
                context(
                    List.of(trip("RADIANT", 1, 800, 930, "WASTED", null)),
                    presence(1, "ABSENT_FAR"))))
        .isEmpty();
  }

  @Test
  void aHeroWhoTookPartInTheFightGivesNothing_aPassiveOneAHalvedSeverity() {
    List<TeamRotationDomain> trips = List.of(trip("RADIANT", 1, 900, 970, "WASTED", null));

    assertThat(contributor.contribute(context(trips, presence(1, "ACTIVE")))).isEmpty();
    assertThat(contributor.contribute(context(trips, presence(1, "PRESENT_PASSIVE"))))
        .extracting(Reason::severity)
        .containsExactly(0.2);
  }

  @Test
  void aHeroWithNoFightRowCountsAsAbsent() {
    assertThat(
            contributor.contribute(context(List.of(trip("RADIANT", 1, 900, 970, "WASTED", null)))))
        .hasSize(1);
  }

  @Test
  void onlyWastedOrDiedTripsOfTheLosingTeamCount() {
    assertThat(
            contributor.contribute(
                context(
                    List.of(
                        trip("RADIANT", 1, 900, 970, "GOOD", null),
                        trip("RADIANT", 1, 900, 970, "NEUTRAL", null),
                        trip("DIRE", 129, 900, 970, "WASTED", null)),
                    presence(1, "ABSENT_FAR"),
                    presence(129, "ABSENT_FAR"))))
        .isEmpty();
  }

  @Test
  void twoTripsOfOneHeroGiveTheMoreSevereAfterTheMergeOfTheAnalyzer() {
    List<Reason> reasons =
        contributor.contribute(
            context(
                List.of(
                    trip("RADIANT", 1, 900, 950, "WASTED", null),
                    trip("RADIANT", 1, 950, 990, "DIED", null)),
                presence(1, "ABSENT_FAR")));

    assertThat(reasons).extracting(Reason::severity).containsExactly(0.4, 0.5);
  }
}
