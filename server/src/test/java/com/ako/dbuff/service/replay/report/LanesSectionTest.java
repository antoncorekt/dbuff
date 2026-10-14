package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanesSectionTest {

  private static final String ULTIMATE = "NO_ULTIMATE_AT_LANE_END";

  private final LaneResultRepo resultRepo = mock(LaneResultRepo.class);
  private final LaneAssignmentRepo assignmentRepo = mock(LaneAssignmentRepo.class);
  private final LaneReasonRepo reasonRepo = mock(LaneReasonRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch = new SectionSwitch(properties, List.of());
  private final LanesSection section =
      new LanesSection(
          resultRepo, assignmentRepo, reasonRepo, heroNames, new LaneProperties(), properties);
  private final List<LaneResultDomain> results = new ArrayList<>();
  private final List<LaneAssignmentDomain> assignments = new ArrayList<>();
  private final List<LaneReasonDomain> reasons = new ArrayList<>();

  LanesSectionTest() {
    when(resultRepo.findByMatchId(1L)).thenReturn(results);
    when(assignmentRepo.findByMatchId(1L)).thenReturn(assignments);
    when(reasonRepo.findByMatchId(1L)).thenReturn(reasons);
    when(heroNames.bySlot(1L))
        .thenReturn(
            Map.of(
                0L, "Phantom Assassin",
                1L, "Sven",
                2L, "Enchantress",
                128L, "Venomancer",
                129L, "Nature's Prophet",
                130L, "Jakiro",
                3L, "Sniper",
                4L, "Riki",
                131L, "Terrorblade"));
  }

  private void result(int pair, String lane, String team, String result, Double margin) {
    results.add(
        LaneResultDomain.builder()
            .matchId(1L)
            .pairId(pair)
            .lane(lane)
            .team(team)
            .result(result)
            .margin(margin)
            .startSeconds(15)
            .endSeconds(650)
            .build());
  }

  private void hero(int pair, long slot, String team) {
    assignments.add(
        LaneAssignmentDomain.builder()
            .matchId(1L)
            .pairId(pair)
            .playerSlot(slot)
            .team(team)
            .build());
  }

  private void reason(
      int pair, String team, String code, String direction, double severity, String evidence) {
    reasons.add(
        LaneReasonDomain.builder()
            .matchId(1L)
            .pairId(pair)
            .team(team)
            .rank(1)
            .code(code)
            .direction(direction)
            .severity(severity)
            .evidence(evidence)
            .build());
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  private void threeLanes() {
    result(2, "TOP", "RADIANT", "WON", 0.15);
    result(2, "TOP", "DIRE", "LOST", -0.15);
    result(1, "MID", "RADIANT", "EVEN", 0.0);
    result(1, "MID", "DIRE", "EVEN", 0.0);
    result(0, "BOTTOM", "RADIANT", "LOST", -0.7);
    result(0, "BOTTOM", "DIRE", "WON", 0.7);
    hero(0, 0, "RADIANT");
    hero(0, 129, "DIRE");
    hero(0, 128, "DIRE");
    hero(1, 2, "RADIANT");
    hero(1, 130, "DIRE");
    hero(2, 3, "RADIANT");
    hero(2, 4, "RADIANT");
    hero(2, 131, "DIRE");
    reason(0, "DIRE", "HARASSMENT", "HELPED", 0.7, "Dire dealt 8252 and took 4978 hero damage");
    reason(0, "DIRE", "DENIES", "HELPED", 0.59, "Dire had 13 denies against 2");
    reason(0, "DIRE", "SUPPORT_PRESENCE", "HELPED", 0.35, "Dire had a support");
    reason(0, "RADIANT", "HARASSMENT", "HURT", 0.95, "Radiant dealt 4978 and took 8252");
    reason(0, "RADIANT", "MATCHUP", "HURT", 0.9, "The kits of Radiant fit worse");
    reason(0, "RADIANT", "STACKED_CAMPS", "HELPED", 1.0, "Radiant stacked a camp");
    reason(0, "DIRE", ULTIMATE, "HELPED", 0.25, "Dire reached it, Radiant had not");
    reason(0, "RADIANT", ULTIMATE, "HURT", 0.25, "Radiant had not reached level 6");
  }

  @Test
  void isOffByDefaultUntilThePropertyIsTrue() {
    assertThat(section.name()).isEqualTo("lanes");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(sectionSwitch.isEnabled(section)).isFalse();

    ReplayReportProperties.SectionToggle on = new ReplayReportProperties.SectionToggle();
    on.setEnabled(true);
    properties.setSections(Map.of("lanes", on));

    assertThat(sectionSwitch.isEnabled(section)).isTrue();
  }

  @Test
  void tellsEachLaneInBottomMidTopOrderWithWinnerMarginHeroesDurationReasonsAndLevelNote() {
    threeLanes();

    List<String> lines = section.lines(input(0L, 128L));

    assertThat(lines)
        .containsExactly(
            "**Laning stage**",
            "Bottom lane (lasted 10:35): Dire won by a lot. Radiant Phantom Assassin, Dire"
                + " Venomancer and Nature's Prophet. Why: Radiant dealt 4978 and took 8252; The"
                + " kits of Radiant fit worse. Dire reached level 6 when the lane ended, Radiant"
                + " did not.",
            "Mid lane (lasted 10:35): even. Radiant Enchantress, Dire Jakiro.",
            "Top lane (lasted 10:35): Radiant won narrowly. Radiant Sniper and Riki, Dire"
                + " Terrorblade.");
  }

  @Test
  void marksTheWinnerAsYourTeamOnlyWhenAllTrackedPlayersAreOnItsSide() {
    threeLanes();

    assertThat(section.lines(input(128L)).get(1)).contains("Dire (your team) won by a lot");
    assertThat(section.lines(input(0L)).get(1)).contains("Dire won by a lot");
    assertThat(section.lines(input(0L, 128L)).get(1)).doesNotContain("your team");
  }

  @Test
  void usesTheConfiguredMarginCutOffs() {
    threeLanes();
    properties.setLaneClearMargin(0.1);
    properties.setLaneBigMargin(0.9);

    List<String> lines = section.lines(input());

    assertThat(lines.get(1)).contains("Dire won clearly");
    assertThat(lines.get(3)).contains("Radiant won clearly");
  }

  @Test
  void skipsLanesWithUnknownTeamLaneOrPairAndSurvivesNullFields() {
    result(0, "BOTTOM", "NOBODY", "WON", 0.5);
    result(1, null, "RADIANT", "WON", 0.5);
    results.add(LaneResultDomain.builder().matchId(1L).lane("TOP").team("RADIANT").build());
    results.add(
        LaneResultDomain.builder()
            .matchId(1L)
            .pairId(3)
            .lane("TOP")
            .team("DIRE")
            .result("WON")
            .build());
    assignments.add(LaneAssignmentDomain.builder().matchId(1L).pairId(3).team("DIRE").build());
    assignments.add(
        LaneAssignmentDomain.builder().matchId(1L).pairId(3).playerSlot(500L).team("DIRE").build());
    reasons.add(LaneReasonDomain.builder().matchId(1L).pairId(3).build());

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly("**Laning stage**", "Top lane: Dire won. Dire an unknown hero.");
  }

  @Test
  void aLaneAloneIsSaidSoAndNoResultsMeanNoLines() {
    assertThat(section.lines(input())).isEmpty();

    result(0, "MID", "RADIANT", "UNCONTESTED", 1.0);

    assertThat(section.lines(input()).get(1))
        .startsWith("Mid lane (lasted 10:35): Radiant was alone");
  }

  @Test
  void showsAtMostTwoReasonsAndCutsALongLineWithAnEllipsis() {
    threeLanes();
    properties.setMaxLaneLineLength(60);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(4);
    assertThat(lines.get(1)).hasSize(60).endsWith("...");
    assertThat(lines.get(1)).startsWith("Bottom lane (lasted 10:35): Dire won by a lot.");
    properties.setMaxLaneLineLength(400);
    assertThat(section.lines(input()).get(1)).doesNotContain("Dire had 13 denies");
  }

  @Test
  void validatesTheLaneProperties() {
    for (int bad : new int[] {59, 601}) {
      properties.setMaxLaneLineLength(bad);
      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("max-lane-line-length");
    }
    properties.setMaxLaneLineLength(60);
    properties.validate();
    properties.setMaxLaneLineLength(600);
    properties.validate();

    double[][] badMargins = {{0, 0.6}, {0.6, 0.3}, {0.3, 0.3}, {0.3, 2.1}};
    for (double[] bad : badMargins) {
      properties.setLaneClearMargin(bad[0]);
      properties.setLaneBigMargin(bad[1]);
      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("lane-clear-margin");
    }
  }
}
