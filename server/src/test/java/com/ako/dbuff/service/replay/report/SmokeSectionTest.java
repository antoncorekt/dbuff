package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
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

class SmokeSectionTest {
  private final SmokeUseRepo repo = mock(SmokeUseRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SmokeSection section = new SmokeSection(repo, heroNames, properties);
  private final List<SmokeUseDomain> rows = new ArrayList<>();
  private long nextId = 1;

  SmokeSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Axe", 1L, "Lion", 2L, "Pudge", 128L, "Sven"));
  }

  private SmokeUseDomain row(long user, String group, int start, String outcome) {
    SmokeUseDomain row =
        SmokeUseDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .team(user >= 128 ? "DIRE" : "RADIANT")
            .userSlot(user)
            .groupSlots(group)
            .startSeconds(start)
            .endSeconds(start + 40)
            .endReason("BROKEN")
            .startZone("LANE")
            .startLane("TOP")
            .endZone("RIVER")
            .outcome(outcome)
            .build();
    rows.add(row);
    return row;
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefault() {
    assertThat(section.name()).isEqualTo("smoke");
    assertThat(section.defaultEnabled()).isFalse();
  }

  @Test
  void writesWhoUsedItWithWhomWhereItWentWhatCameOfItAndHowItEnded() {
    row(0, "0,1, 2", 720, "PAID_OFF");

    List<String> lines = section.lines(input(1L));

    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).isEqualTo("**Smoke**");
    assertThat(lines.get(1))
        .isEqualTo(
            "12:00 to 12:40 Axe (Radiant, your team) used a Smoke of Deceit with Lion and Pudge:"
                + " from the top lane to the river; paid off; the smoke broke.");
  }

  @Test
  void saysAloneWhenTheGroupIsOnlyTheUserAndNotKnownWhenTheLogDoesNotSay() {
    SmokeUseDomain r = row(128, "128", 300, "BACKFIRED");
    r.setEndReason("UNKNOWN");
    r.setEndZone(null);

    String line = section.lines(input()).get(1);

    assertThat(line)
        .contains("Sven (Dire) used a Smoke of Deceit alone")
        .contains("started in the top lane, end not known; backfired; how it ended is not known.");
  }

  @Test
  void capsAtThreePreferringUsesThatPaidOffOrBackfiredAndListsInTimeOrder() {
    row(0, "0", 100, "NOTHING");
    row(0, "0", 200, "NOTHING");
    row(1, "1", 300, "PAID_OFF");
    row(1, "1", 400, "NOTHING");
    row(2, "2", 500, "BACKFIRED");

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(4);
    assertThat(lines.get(1)).startsWith("1:40 to").contains("came to nothing");
    assertThat(lines.get(2)).startsWith("5:00 to").contains("paid off");
    assertThat(lines.get(3)).startsWith("8:20 to").contains("backfired");
  }

  @Test
  void aTieBreaksOnTheRowId() {
    properties.setMaxSmokeLines(1);
    row(0, "0", 100, "PAID_OFF").setEndZone("BASE");
    row(0, "0", 100, "PAID_OFF");
    List<SmokeUseDomain> reversed = new ArrayList<>(rows);
    java.util.Collections.reverse(reversed);
    when(repo.findByMatchId(1L)).thenReturn(reversed);

    assertThat(section.lines(input()).get(1)).contains("to the base");
  }

  @Test
  void aMatchWithNoSmokeAndNullDataPrintNothingOrDoNotThrow() {
    assertThat(section.lines(input())).isEmpty();
    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();

    rows.clear();
    when(repo.findByMatchId(1L)).thenReturn(rows);
    rows.add(SmokeUseDomain.builder().groupSlots("x,,3, ").build());
    rows.add(SmokeUseDomain.builder().build());
    rows.add(null);

    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1))
        .contains("? to ? ? (An unknown team) used a Smoke of Deceit")
        .contains("Radiant player 4")
        .contains("not known");
  }

  @Test
  void validatesTheLineCap() {
    for (int bad : new int[] {0, 11}) {
      properties.setMaxSmokeLines(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-smoke-lines");
    }
    properties.setMaxSmokeLines(10);
    properties.validate();
    properties.setMaxBehaviourLineLength(59);
    assertThatThrownBy(properties::validate).hasMessageContaining("max-behaviour-line-length");
  }

  @Test
  void anUnreadableGroupIsNeverCalledAlone() {
    row(0, null, 100, "PAID_OFF");
    row(0, " ", 200, "PAID_OFF");
    row(0, "x,y", 300, "PAID_OFF");
    properties.setMaxSmokeLines(5);

    List<String> lines = section.lines(input());

    assertThat(lines.subList(1, 4))
        .allSatisfy(l -> assertThat(l).contains("with a group that is not known"));
  }

  @Test
  void aPartlyCorruptGroupNamesTheReadableHeroesAndAdmitsTheRest() {
    row(0, "0,1,oops", 100, "PAID_OFF");
    row(0, "0,oops", 200, "PAID_OFF");
    properties.setMaxSmokeLines(5);

    List<String> lines = section.lines(input());

    assertThat(lines.get(1)).contains("with Lion and others who are not known");
    assertThat(lines.get(2)).contains("with others who are not known").doesNotContain("alone");
  }

  @Test
  void anUnknownOrMissingOutcomeRanksBelowNothingWhenTheCapBites() {
    properties.setMaxSmokeLines(2);
    row(0, "0", 100, null);
    row(0, "0", 200, "WHATEVER");
    row(0, "0", 300, "NOTHING");
    row(0, "0", 400, "BACKFIRED");

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).startsWith("5:00 to").contains("came to nothing");
    assertThat(lines.get(2)).startsWith("6:40 to").contains("backfired");
  }

  @Test
  void view_countsWhatTheSmokesCameToAndKeepsPlainTextAsItWas() {
    row(0, "0,1, 2", 720, "PAID_OFF");
    row(128, "128", 900, "BACKFIRED");

    SectionView mine = section.view(input(1L)).orElseThrow();
    SectionView both = section.view(input()).orElseThrow();

    ViewRendering.assertWithinLimits(mine);
    assertThat(mine.title()).isEqualTo("Smoke");
    assertThat(mine.verdict())
        .isEqualTo("Your team's Smokes of Deceit: 1 paid off, 0 backfired, 0 came to nothing");
    assertThat(mine.accent()).isEqualTo(Accent.WIN);
    assertThat(both.verdict())
        .isEqualTo("Smokes of Deceit, both teams: 1 paid off, 1 backfired, 0 came to nothing");
    assertThat(both.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(new PlainViewRenderer().lines(both)).isEqualTo(section.lines(input()));
    assertThat(section.lines(input(1L)).get(1))
        .isEqualTo(
            "12:00 to 12:40 Axe (Radiant, your team) used a Smoke of Deceit with Lion and Pudge:"
                + " from the top lane to the river; paid off; the smoke broke.");
  }

  @Test
  void view_isRedWhenTheTrackedTeamsSmokesBackfired() {
    row(128, "128", 300, "BACKFIRED");

    assertThat(section.view(input(130L)).orElseThrow().accent()).isEqualTo(Accent.LOSS);
  }

  @Test
  void view_isEmptyWithoutSmoke() {
    assertThat(section.view(input())).isEmpty();
  }
}
