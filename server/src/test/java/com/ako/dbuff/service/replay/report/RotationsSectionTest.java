package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RotationsSectionTest {
  private final TeamRotationRepo repo = mock(TeamRotationRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final RotationsSection section = new RotationsSection(repo, heroNames, properties);
  private final List<TeamRotationDomain> rows = new ArrayList<>();
  private long nextId = 1;

  RotationsSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Axe", 1L, "Lion", 128L, "Pudge"));
  }

  private TeamRotationDomain row(long slot, int start, String verdict, String reasons) {
    TeamRotationDomain row =
        TeamRotationDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .team(slot >= 128 ? "DIRE" : "RADIANT")
            .playerSlot(slot)
            .startSeconds(start)
            .endSeconds(start + 40)
            .fromZone("LANE")
            .fromLane("TOP")
            .toZone("LANE")
            .toLane("MID")
            .kills(0)
            .fightsWon(0)
            .reasons(reasons)
            .verdict(verdict)
            .build();
    rows.add(row);
    return row;
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultAndIndependentOfOtherSections() {
    assertThat(section.name()).isEqualTo("rotations");
    assertThat(section.defaultEnabled()).isFalse();
  }

  @Test
  void writesOneLineInPlainWordsWithTheWhySentences() {
    TeamRotationDomain r = row(0, 750, "WASTED", "DID_NOTHING,INCOME_DROP");
    r.setIncomeBefore(620);
    r.setIncomeDuring(1250);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).isEqualTo("**Team rotations**");
    assertThat(lines.get(1))
        .isEqualTo(
            "12:30 Axe (Radiant) went from top lane to mid lane: wasted. Why: Axe did nothing"
                + " there; Axe's farm dropped from 620 to 1.3k gold per minute (experience from ?"
                + " to ? per minute).");
  }

  @Test
  void marksYourTeamAndNamesNoCodes() {
    row(128, 300, "DIED", "DIED,LEFT_LANE_UNDEFENDED,TOWER_LOST_WHILE_AWAY");

    String line = section.lines(input(130L)).get(1);

    assertThat(line)
        .contains("Pudge (Dire, your team)")
        .contains("died")
        .contains("Pudge died on the trip")
        .contains("the lane Pudge left lost the creep count (his partners ?, the enemy ?)")
        .contains("an own tower fell while Pudge was away")
        .doesNotContain("DIED")
        .doesNotContain("_");
    assertThat(section.lines(input(0L)).get(1)).contains("(Dire)").doesNotContain("your team");
  }

  @Test
  void listsDiedThenWastedThenTheBiggestGoodAndLeavesOutNeutralThenShowsTimeOrder() {
    properties.setMaxRotationLines(4);
    row(0, 100, "NEUTRAL", "");
    row(0, 200, "GOOD", "");
    TeamRotationDomain bigGood = row(1, 300, "GOOD", "");
    bigGood.setKills(2);
    bigGood.setFightsWon(1);
    row(0, 400, "WASTED", "DID_NOTHING");
    row(1, 500, "DIED", "DIED");
    row(0, 600, "GOOD", "");

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(5);
    assertThat(lines.get(1)).startsWith("3:20 Axe").contains("good");
    assertThat(lines.get(2)).startsWith("5:00 Lion").contains("good");
    assertThat(lines.get(3)).startsWith("6:40 Axe").contains("wasted");
    assertThat(lines.get(4)).startsWith("8:20 Lion").contains("died");
  }

  @Test
  void aTieBreaksOnTheRowIdSoTheOrderNeverDependsOnTheRepository() {
    properties.setMaxRotationLines(1);
    row(0, 300, "DIED", "DIED");
    row(0, 300, "DIED", "DIED").setIncomeBefore(7);
    TeamRotationDomain first = rows.get(0);
    first.setToLane("BOTTOM");
    List<TeamRotationDomain> reversed = new ArrayList<>(rows);
    java.util.Collections.reverse(reversed);
    when(repo.findByMatchId(1L)).thenReturn(reversed);

    assertThat(section.lines(input()).get(1)).contains("bottom lane");
  }

  @Test
  void emptyAndNullDataPrintNothingAndNullFieldsDoNotThrow() {
    assertThat(section.lines(input())).isEmpty();
    row(0, 100, "NEUTRAL", "");
    assertThat(section.lines(input())).isEmpty();
    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();

    rows.clear();
    when(repo.findByMatchId(1L)).thenReturn(rows);
    rows.add(TeamRotationDomain.builder().verdict("DIED").build());
    rows.add(TeamRotationDomain.builder().verdict("GOOD").reasons("DIED,NOPE,INCOME_DROP").build());
    rows.add(TeamRotationDomain.builder().verdict("BROKEN").build());
    rows.add(null);

    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).contains("? ? (An unknown team)").contains("died");
  }

  @Test
  void aLongLineIsCutAtAWordBoundaryWithAnEllipsis() {
    row(0, 300, "WASTED", "DID_NOTHING,INCOME_DROP,TOWER_LOST_WHILE_AWAY");
    properties.setMaxBehaviourLineLength(100);

    String line = section.lines(input()).get(1);

    assertThat(line).hasSizeLessThanOrEqualTo(100).endsWith("...").doesNotEndWith(" ...");
  }

  @Test
  void validatesTheLineCaps() {
    for (int bad : new int[] {0, 11}) {
      properties.setMaxRotationLines(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-rotation-lines");
    }
    properties.setMaxRotationLines(1);
    properties.validate();
    properties.setMaxRotationLines(10);
    properties.validate();
  }
}
