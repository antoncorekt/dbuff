package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.roleloop.RoleLoopReport.MatchData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLoopReportTest {
  private static final long MATCH = 77L;

  private final MatchDomain match =
      MatchDomain.builder()
          .id(MATCH)
          .startTime(1_760_227_200L)
          .radiantWin(true)
          .radiantScore(35L)
          .direScore(28L)
          .build();

  private List<PlayerMatchStatisticDomain> stats() {
    List<PlayerMatchStatisticDomain> stats = new ArrayList<>();
    long[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    for (long slot : slots) {
      stats.add(
          PlayerMatchStatisticDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .heroId(10 + slot)
              .laneRole(1L + slot % 3)
              .positionEst(1L + slot % 5)
              .netWorth(20_000L - 1_000 * (slot % 5))
              .build());
    }
    return stats;
  }

  private MatchData data(
      List<PlayerRoleDomain> roles,
      List<PlayerRoleProfileDomain> profiles,
      List<TeamCompositionDomain> compositions,
      List<AbilityDraftPickDomain> picks) {
    return new MatchData(
        MATCH,
        match,
        "Ability Draft",
        stats(),
        List.of(
            ReplayPlayerDomain.builder()
                .matchId(MATCH)
                .playerSlot(0L)
                .heroEntityName("Sniper")
                .build()),
        roles,
        profiles,
        compositions,
        picks);
  }

  private PlayerRoleProfileDomain profile(long slot, String stage, int role, Double farm) {
    return PlayerRoleProfileDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .stage(stage)
        .role(role)
        .fitRole(role)
        .confidence(0.64)
        .lane("SAFE")
        .farmPriority(farm)
        .roleScores("1.50,0.20,0.10,0.00,0.00")
        .evidence("nwRank=1")
        .build();
  }

  @Test
  void headerNamesTheMatchModeDateTeamsAndWinner() {
    String report = RoleLoopReport.render(data(List.of(), List.of(), List.of(), List.of()));

    assertThat(report.lines().findFirst().orElseThrow())
        .isEqualTo(
            "# Match 77 | Ability Draft | 2025-10-12 | Radiant 35 : 28 Dire | winner Radiant");
  }

  @Test
  void aModeWithoutAResolverSaysSoInsteadOfPrintingBlanks() {
    String report = RoleLoopReport.render(data(List.of(), List.of(), List.of(), List.of()));

    assertThat(report).contains("No roles stored (no resolver for this mode).");
    assertThat(report).contains("| 0 | Sniper | n/a | none | n/a | n/a |");
    assertThat(report).contains("| RADIANT | PICK | none stored |");
  }

  @Test
  void finalOnlyRowsSayThereIsNoIntent() {
    String report =
        RoleLoopReport.render(
            data(
                List.of(
                    PlayerRoleDomain.builder()
                        .matchId(MATCH)
                        .playerSlot(0L)
                        .role(1)
                        .source("DERIVED")
                        .build()),
                List.of(profile(0, "FINAL", 1, 0.9)),
                List.of(
                    TeamCompositionDomain.builder()
                        .matchId(MATCH)
                        .team("RADIANT")
                        .stage("FINAL")
                        .tokens("")
                        .summary("Balanced.")
                        .build()),
                List.of()));

    assertThat(report).contains("No intent rows");
    assertThat(report).contains("- intent: n/a (none stored)");
    assertThat(report).contains("| 0 | Sniper | n/a | 1 | n/a | 1(1.50) 2(0.20) 3(0.10) |");
    assertThat(report).contains("| RADIANT | FINAL | no findings |");
    assertThat(report).contains("none stored (needs intent rows for all five players)");
    assertThat(report).contains("summary: Balanced.");
  }

  @Test
  void aPlayerShowsBothStagesWithKnownAndUnknownFunctionsAndTheBaseline() {
    String report =
        RoleLoopReport.render(
            data(
                List.of(
                    PlayerRoleDomain.builder()
                        .matchId(MATCH)
                        .playerSlot(0L)
                        .role(3)
                        .source("DERIVED")
                        .build()),
                List.of(profile(0, "INTENT", 1, 0.9), profile(0, "FINAL", 3, null)),
                List.of(
                    TeamCompositionDomain.builder()
                        .matchId(MATCH)
                        .team("DIRE")
                        .stage("PICK")
                        .tokens("CARRIES_3,NO_INITIATOR")
                        .summary("Three carries.")
                        .build()),
                List.of(
                    AbilityDraftPickDomain.builder()
                        .matchId(MATCH)
                        .playerSlot(0L)
                        .pickOrder(2)
                        .abilityName("sniper_shrapnel")
                        .build(),
                    AbilityDraftPickDomain.builder()
                        .matchId(MATCH)
                        .playerSlot(0L)
                        .pickOrder(1)
                        .abilityId(5L)
                        .build())));

    assertThat(report)
        .contains("### RADIANT slot 0, Sniper")
        .contains("| 0 | Sniper | 1 | 3 | 1(1.50) 2(0.20) 3(0.10) | 1(1.50) 2(0.20) 3(0.10) |")
        .contains("- intent: role 1 (confidence 0.64), fitRole 1, lane SAFE")
        .contains("farmPriority 0.90, jungle unknown")
        .contains("farmPriority unknown")
        .contains("roleScores 1.50,0.20,0.10,0.00,0.00")
        .contains("evidence: nwRank=1")
        .contains("- drafted skills: ability 5, sniper_shrapnel")
        .contains(
            "- OpenDota: position_est 1, lane_role 1, net worth rank 1 of 5 (net worth 20000)")
        .contains("| DIRE | PICK | CARRIES_3,NO_INITIATOR |")
        .contains("tokens: CARRIES_3,NO_INITIATOR");
  }

  @Test
  void theRolesTableShowsTheThreeBestFitsNextToTheAssignedRole() {
    PlayerRoleProfileDomain leftover = profile(0, "FINAL", 5, 0.5);
    leftover.setRoleScores("-0.50,2.00,3.25,1.10,-2.00");

    String report = RoleLoopReport.render(data(List.of(), List.of(leftover), List.of(), List.of()));

    assertThat(report)
        .contains("| slot | hero | intent | final | intent fit | final fit |")
        .contains("| 0 | Sniper | n/a | 5 | n/a | 3(3.25) 2(2.00) 4(1.10) |");
  }

  @Test
  void aHeroWithoutAReplayNameFallsBackToItsId() {
    String report = RoleLoopReport.render(data(List.of(), List.of(), List.of(), List.of()));

    assertThat(report).contains("| 1 | hero 11 |");
  }

  @Test
  void anUnavailableMatchIsReportedWithItsReason() {
    assertThat(RoleLoopReport.unavailable(5L, "no fixture"))
        .isEqualTo("# Match 5\n\nNot reported: no fixture\n");
  }

  @Test
  void aNullRoleOnAnExistingRowReadsUnknownNotNull() {
    String report =
        RoleLoopReport.render(
            data(List.of(), List.of(profile(0, "FINAL", 1, 0.5)), List.of(), List.of()));
    PlayerRoleProfileDomain noRole = profile(0, "FINAL", 1, 0.5);
    noRole.setRole(null);
    noRole.setFitRole(null);

    String withNull = RoleLoopReport.render(data(List.of(), List.of(noRole), List.of(), List.of()));

    assertThat(withNull)
        .contains("| 0 | Sniper | n/a | unknown | n/a | 1(1.50) 2(0.20) 3(0.10) |")
        .doesNotContain("null");
    assertThat(withNull).contains("role unknown (confidence 0.64), fitRole unknown");
    assertThat(report).contains("| 0 | Sniper | n/a | 1 | n/a | 1(1.50) 2(0.20) 3(0.10) |");
  }

  @Test
  void aPipeInAValueDoesNotSplitTheRow() {
    String report =
        RoleLoopReport.render(
            data(
                List.of(),
                List.of(),
                List.of(
                    TeamCompositionDomain.builder()
                        .matchId(MATCH)
                        .team("DIRE")
                        .stage("FINAL")
                        .tokens("A|B\nC")
                        .build()),
                List.of()));

    assertThat(report).contains("| DIRE | FINAL | A\\|B C |");
  }

  @Test
  void aMatchWithoutPlayerStatisticsSaysSo() {
    MatchData noStats =
        new MatchData(
            MATCH,
            match,
            "All Pick",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String report = RoleLoopReport.render(noStats);

    assertThat(report).contains("**No player statistics stored.**").doesNotContain("## Roles");
  }

  @Test
  void anUnavailableReportIsRecognisedAndItsReasonRead() {
    String report = RoleLoopReport.unavailable(5L, "skipped: a\nb");

    assertThat(RoleLoopReport.isUnavailable(report)).isTrue();
    assertThat(RoleLoopReport.reasonOf(report)).isEqualTo("skipped: a b");
    assertThat(
            RoleLoopReport.isUnavailable(
                RoleLoopReport.render(data(List.of(), List.of(), List.of(), List.of()))))
        .isFalse();
  }
}
