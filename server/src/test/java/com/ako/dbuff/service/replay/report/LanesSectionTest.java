package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
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
    when(heroNames.iconBySlot(1L))
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

  private void wonBottom() {
    result(0, "BOTTOM", "RADIANT", "WON", 0.5);
    result(0, "BOTTOM", "DIRE", "LOST", -0.5);
    hero(0, 0, "RADIANT");
    hero(0, 128, "DIRE");
  }

  @Test
  void dropsTheSecondReasonThenTheWholeWhyBeforeCuttingAndKeepsTheLevelNote() {
    wonBottom();
    reason(0, "RADIANT", "A", "HELPED", 0.9, "First reason is here");
    reason(0, "RADIANT", "B", "HELPED", 0.8, "Second reason is here");
    reason(0, "RADIANT", ULTIMATE, "HELPED", 0.2, "x");
    String fixed =
        "Bottom lane (lasted 10:35): Radiant won clearly. Radiant Phantom Assassin, Dire"
            + " Venomancer.";
    String note = " Radiant reached level 6 when the lane ended, Dire did not.";
    String both = fixed + " Why: First reason is here; Second reason is here." + note;
    String one = fixed + " Why: First reason is here." + note;
    String none = fixed + note;

    properties.setMaxLaneLineLength(both.length());
    assertThat(section.lines(input()).get(1)).isEqualTo(both);
    properties.setMaxLaneLineLength(both.length() - 1);
    assertThat(section.lines(input()).get(1)).isEqualTo(one);
    properties.setMaxLaneLineLength(one.length() - 1);
    assertThat(section.lines(input()).get(1)).isEqualTo(none);
  }

  @Test
  void lastResortCutsAtAWordBoundaryNeverInsideAWordOrSurrogatePair() {
    wonBottom();
    properties.setMaxLaneLineLength(60);

    String line = section.lines(input()).get(1);

    assertThat(line).hasSizeLessThanOrEqualTo(60).endsWith("...");
    assertThat(line.substring(0, line.length() - 3))
        .isEqualTo("Bottom lane (lasted 10:35): Radiant won clearly. Radiant");
  }

  @Test
  void aCutNeverSplitsASurrogatePairOrAWordWithoutSpaces() {
    String text = "X".repeat(10) + "\uD83D\uDE00".repeat(20);

    for (int max = 8; max < 30; max++) {
      String cut = LineText.cut(text, max);
      assertThat(cut).hasSizeLessThanOrEqualTo(max).endsWith("...");
      assertThat(Character.isHighSurrogate(cut.charAt(cut.length() - 4))).isFalse();
    }
  }

  @Test
  void aSameCodeAboutDifferentHeroesKeepsBothButTheSameFactFromBothSidesIsSaidOnce() {
    wonBottom();
    reasons.add(
        LaneReasonDomain.builder()
            .pairId(0)
            .team("DIRE")
            .code("DIED")
            .direction("HURT")
            .subjectSlot(128L)
            .severity(0.9)
            .evidence("Venomancer died at 2:30")
            .build());
    reasons.add(
        LaneReasonDomain.builder()
            .pairId(0)
            .team("DIRE")
            .code("DIED")
            .direction("HURT")
            .subjectSlot(129L)
            .severity(0.8)
            .evidence("Nature's Prophet died at 3:00")
            .build());
    reason(0, "RADIANT", "HARASSMENT", "HELPED", 0.5, "Radiant dealt 5 and took 3");
    reason(0, "DIRE", "HARASSMENT", "HURT", 0.5, "Dire dealt 3 and took 5");

    assertThat(section.lines(input()).get(1))
        .contains("Why: Venomancer died at 2:30; Nature's Prophet died at 3:00.");

    properties.setMaxLaneLineLength(600);
    reasons.removeIf(r -> "DIED".equals(r.getCode()));
    String line = section.lines(input()).get(1);
    assertThat(line).contains("Radiant dealt 5 and took 3").doesNotContain("Dire dealt 3");
  }

  @Test
  void keepsTheLongestLastingPairWhenALaneHasSeveralAndNeverMoreThanThreeLanes() {
    result(0, "TOP", "RADIANT", "WON", 0.5);
    result(1, "TOP", "RADIANT", "LOST", -0.5);
    results.get(1).setEndSeconds(900);
    result(2, "MID", "RADIANT", "EVEN", 0.0);
    result(3, "BOTTOM", "RADIANT", "EVEN", 0.0);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(4);
    assertThat(lines.get(1)).startsWith("Bottom lane");
    assertThat(lines.get(3)).startsWith("Top lane (lasted 14:45): even");
  }

  @Test
  void skipsAStoredLaneThatIsNotKnown() {
    result(0, "SIDELINE", "RADIANT", "WON", 0.5);

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void doesNotLoadReasonsWhenNoLaneHasAWinner() {
    result(0, "MID", "RADIANT", "EVEN", 0.0);

    section.lines(input());

    org.mockito.Mockito.verify(reasonRepo, org.mockito.Mockito.never()).findByMatchId(1L);
  }

  @Test
  void commonSideIsNullSafe() {
    assertThat(TeamSide.commonSide(null)).isNull();
    assertThat(TeamSide.commonSide(Set.of())).isNull();
    assertThat(TeamSide.commonSide(new java.util.HashSet<>(java.util.Arrays.asList(null, 130L))))
        .isEqualTo(TeamSide.DIRE);
    assertThat(TeamSide.commonSide(Set.of(1L, 130L))).isNull();
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

    double nan = Double.NaN;
    double inf = Double.POSITIVE_INFINITY;
    double[][] badMargins = {
      {0, 0.6}, {0.6, 0.3}, {0.3, 0.3}, {0.3, 2.1}, {nan, 0.6}, {0.3, nan}, {0.3, inf}, {-inf, 0.6}
    };
    for (double[] bad : badMargins) {
      properties.setLaneClearMargin(bad[0]);
      properties.setLaneBigMargin(bad[1]);
      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("lane-clear-margin");
    }
  }

  @Test
  void view_sayswhoWonMoreLanesAndKeepsPlainTextAsItWas() {
    threeLanes();

    SectionView both = section.view(input(0L, 128L)).orElseThrow();
    SectionView dire = section.view(input(128L)).orElseThrow();

    ViewRendering.assertWithinLimits(both);
    assertThat(both.title()).isEqualTo("Laning stage");
    assertThat(both.verdict()).isEqualTo("Radiant and Dire won 1 each; 1 lane even");
    assertThat(both.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(dire.verdict())
        .isEqualTo("Dire (your team) split 1 to 1 in decided lanes; 1 lane even");
    assertThat(both.bullets()).hasSize(3);
    assertThat(String.join("\n", section.lines(input(0L, 128L))))
        .isEqualTo(
            "**Laning stage**\n"
                + "Bottom lane (lasted 10:35): Dire won by a lot. Radiant Phantom Assassin, Dire"
                + " Venomancer and Nature's Prophet. Why: Radiant dealt 4978 and took 8252; The"
                + " kits of Radiant fit worse. Dire reached level 6 when the lane ended, Radiant"
                + " did not.\n"
                + "Mid lane (lasted 10:35): even. Radiant Enchantress, Dire Jakiro.\n"
                + "Top lane (lasted 10:35): Radiant won narrowly. Radiant Sniper and Riki, Dire"
                + " Terrorblade.");
    assertThat(new PlainViewRenderer().lines(both)).isEqualTo(section.lines(input(0L, 128L)));
  }

  @Test
  void view_isGreenWhenTheTrackedTeamWonMostLanes() {
    result(0, "BOTTOM", "RADIANT", "WON", 0.7);
    result(0, "BOTTOM", "DIRE", "LOST", -0.7);
    result(1, "MID", "RADIANT", "EVEN", 0.0);
    result(1, "MID", "DIRE", "EVEN", 0.0);

    SectionView view = section.view(input(0L)).orElseThrow();

    assertThat(view.verdict())
        .isEqualTo("Radiant (your team) won 1 of 1 decided lanes; 1 lane even");
    assertThat(view.accent()).isEqualTo(Accent.WIN);
  }

  @Test
  void view_saysWhenNoLaneHadAWinner() {
    result(1, "MID", "RADIANT", "EVEN", 0.0);
    result(1, "MID", "DIRE", "EVEN", 0.0);

    assertThat(section.view(input()).orElseThrow().verdict())
        .isEqualTo("No lane had a clear winner");
  }

  @Test
  void view_isEmptyWithoutLanes() {
    assertThat(section.view(input())).isEmpty();
  }
}
