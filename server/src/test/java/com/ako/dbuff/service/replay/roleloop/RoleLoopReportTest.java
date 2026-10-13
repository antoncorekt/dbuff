package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff;
import com.ako.dbuff.service.replay.roleloop.RoleLoopReport.MatchData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        Map.of(0L, "Sniper"),
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

    assertThat(report)
        .contains(
            "**Summary:** 10 players, no draft intent in this mode; 0 of 0 team verdicts have"
                + " findings.")
        .contains("**How to read this:**");
    assertThat(report).contains("No roles stored (no resolver for this mode).");
    assertThat(report).contains("| Sniper (Radiant) | none | n/a | n/a | n/a | | |");
    assertThat(report).contains("| Radiant at the pick | none stored | | |");
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
    assertThat(report)
        .contains(
            "| Sniper (Radiant) | 1 carry | n/a | carry 1.50, mid 0.20, offlane 0.10 | n/a | | |");
    assertThat(report).contains("| Radiant final | no findings | | |");
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
        .contains("### Sniper (Radiant)")
        .contains(
            "| Sniper (Radiant) | 3 offlane | 1 carry | carry 1.50, mid 0.20, offlane 0.10 | carry"
                + " 1.50, mid 0.20, offlane 0.10 | | |")
        .contains("- intent: role 1 carry (confidence 0.64), best fit 1 carry, lane safe")
        .contains("farm priority 0.90, jungle unknown")
        .contains("farm priority unknown")
        .contains("scores: 1 carry 1.50, 2 mid 0.20, 3 offlane 0.10, 4 soft support 0.00")
        .contains("evidence: nwRank=1")
        .contains("- drafted skills: ability 5, Sniper Shrapnel")
        .contains("- OpenDota: position 1, safe lane, net worth rank 1 of 5 (net worth 20k)")
        .contains("| Dire at the pick | 3 carries, no initiator | | |")
        .contains("findings: 3 carries, no initiator");
  }

  @Test
  void theRolesTableShowsTheThreeBestFitsNextToTheAssignedRole() {
    PlayerRoleProfileDomain leftover = profile(0, "FINAL", 5, 0.5);
    leftover.setRoleScores("-0.50,2.00,3.25,1.10,-2.00");

    String report = RoleLoopReport.render(data(List.of(), List.of(leftover), List.of(), List.of()));

    assertThat(report)
        .contains(
            "| player | final role | intent role | final fit | intent fit | assistant | owner |")
        .contains(
            "| Sniper (Radiant) | 5 hard support | n/a | offlane 3.25, mid 2.00, soft support 1.10"
                + " | n/a | | |");
  }

  @Test
  void aHeroWithoutAKnownNameIsNamedByItsTeamAndPlaceNotByItsId() {
    String report = RoleLoopReport.render(data(List.of(), List.of(), List.of(), List.of()));

    assertThat(report).contains("| Radiant player 2 (Radiant) |").doesNotContain("hero 11");
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
        .contains(
            "| Sniper (Radiant) | unknown | n/a | carry 1.50, mid 0.20, offlane 0.10 | n/a | | |")
        .doesNotContain("null");
    assertThat(withNull).contains("role unknown (confidence 0.64), best fit unknown");
    assertThat(report).contains("| Sniper (Radiant) | 1 carry | n/a | carry 1.50");
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

    assertThat(report).contains("| Dire final | a\\|b c | | |");
  }

  @Test
  void aMatchWithoutPlayerStatisticsSaysSo() {
    MatchData noStats =
        new MatchData(
            MATCH,
            match,
            "All Pick",
            List.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String report = RoleLoopReport.render(noStats);

    assertThat(report).contains("**No player statistics stored.**").doesNotContain("## Roles");
  }

  @Test
  void theTopicSectionsAreFoundInARealReport() {
    String before =
        RoleLoopReport.render(
            data(List.of(), List.of(profile(0, "FINAL", 1, 0.5)), List.of(), List.of()));
    String after = before.replace("| Sniper (Radiant) | 1 carry |", "| Sniper (Radiant) | 2 mid |");

    assertThat(ReviewLoopDiff.changes(before, before, RoleLoopTopic.SECTIONS)).isEmpty();
    assertThat(ReviewLoopDiff.changes(before, after, RoleLoopTopic.SECTIONS))
        .containsExactly("Sniper (Radiant), final role: 1 carry -> 2 mid");
  }
}
