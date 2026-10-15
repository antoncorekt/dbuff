package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutliersSectionTest {
  private final PlayerOutlierRepo repo = mock(PlayerOutlierRepo.class);
  private final TeamRotationRepo rotationRepo = mock(TeamRotationRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch = new SectionSwitch(properties, List.of());
  private final OutliersSection section =
      new OutliersSection(repo, rotationRepo, heroNames, properties);
  private final List<PlayerOutlierDomain> rows = new ArrayList<>();
  private final List<TeamRotationDomain> rotations = new ArrayList<>();
  private long nextId = 1;

  OutliersSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(rotationRepo.findByMatchId(1L)).thenReturn(rotations);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Sven", 129L, "Mirana"));
  }

  private PlayerOutlierDomain outlier(long slot, String kind, int start, int end, double share) {
    PlayerOutlierDomain row =
        PlayerOutlierDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .playerSlot(slot)
            .kind(kind)
            .startSeconds(start)
            .endSeconds(end)
            .sharePercent(share)
            .build();
    rows.add(row);
    return row;
  }

  private void rotation(long slot, int start, String verdict) {
    rotation(slot, start, null, verdict);
  }

  private void rotation(long slot, int start, Integer end, String verdict) {
    rotations.add(
        TeamRotationDomain.builder()
            .id(nextId++)
            .playerSlot(slot)
            .startSeconds(start)
            .endSeconds(end)
            .verdict(verdict)
            .build());
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultUntilThePropertyIsTrue() {
    assertThat(section.name()).isEqualTo("outliers");
    assertThat(section.order()).isEqualTo(78);
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(sectionSwitch.isEnabled(section)).isFalse();

    ReplayReportProperties.SectionToggle on = new ReplayReportProperties.SectionToggle();
    on.setEnabled(true);
    properties.setSections(Map.of("outliers", on));

    assertThat(sectionSwitch.isEnabled(section)).isTrue();
  }

  @Test
  void saysAnOutlierWithItsExplanationAndMarksYourTeam() {
    outlier(0, "NW_OUTLIER", 1440, 1800, 41.2)
        .setExplanation("his income came mostly from jungle creeps");

    assertThat(section.lines(input(0L)))
        .containsExactly(
            "**Net worth outliers**",
            "Sven held 41% of the net worth of Radiant (your team) from 24:00 (his income came"
                + " mostly from jungle creeps)");
    assertThat(section.lines(input())).last().asString().contains("of Radiant from 24:00");
  }

  @Test
  void aFeederGetsItsWastedRotationsCountedInItsStretch() {
    outlier(129, "FEEDER", 600, 900, 7.0).setExplanation("he died 9 times in that stretch");
    rotation(129, 650, "WASTED");
    rotation(129, 700, "DIED");
    rotation(129, 720, "GOOD");
    rotation(129, 5000, "WASTED");
    rotation(0, 650, "WASTED");

    assertThat(section.lines(input()))
        .containsExactly(
            "**Net worth outliers**",
            "Mirana held only 7% of the net worth of Dire from 10:00 (he died 9 times in that"
                + " stretch; 2 of his 3 rotations were wasted or ended in a death)");
  }

  @Test
  void aRotationOverlappingTheWindowCountsAndOneEndingBeforeItDoesNot() {
    outlier(129, "FEEDER", 600, 900, 7.0).setExplanation("he died 9 times in that stretch");
    rotation(129, 500, 650, "WASTED");
    rotation(129, 300, 599, "WASTED");
    rotation(129, 960, 1000, "DIED");
    rotation(129, 961, 1000, "DIED");

    assertThat(section.lines(input()).get(1))
        .endsWith("2 of his 2 rotations were wasted or ended in a death)");
  }

  @Test
  void rotationsAreLoadedOnlyForAFeederLine() {
    outlier(0, "NW_OUTLIER", 1440, 1800, 41);

    section.lines(input());

    verify(rotationRepo, never()).findByMatchId(1L);
  }

  @Test
  void aRowWithoutAnExplanationIsListedWithTheShareOnly() {
    outlier(0, "NW_OUTLIER", 1440, 1800, 41);

    assertThat(section.lines(input()))
        .containsExactly(
            "**Net worth outliers**", "Sven held 41% of the net worth of Radiant from 24:00");
  }

  @Test
  void nothingStoredOrNothingUsableIsNoLines() {
    assertThat(section.lines(input())).isEmpty();
    rows.add(PlayerOutlierDomain.builder().matchId(1L).build());
    rows.add(null);
    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void capsTheLinesYourTeamFirstThenInTimeOrder() {
    properties.setMaxOutlierLines(2);
    outlier(129, "FEEDER", 100, 700, 5);
    outlier(0, "NW_OUTLIER", 1800, 2000, 40);
    outlier(0, "NW_OUTLIER", 900, 1000, 40);

    List<String> lines = section.lines(input(0L));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).contains("from 15:00");
    assertThat(lines.get(2)).contains("from 30:00");
  }

  @Test
  void theCapIsValidated() {
    properties.setMaxOutlierLines(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("max-outlier-lines");
    properties.setMaxOutlierLines(11);
    assertThatThrownBy(properties::validate).hasMessageContaining("max-outlier-lines");
  }

  @Test
  void view_countsTheOutliersAndKeepsPlainTextAsItWas() {
    outlier(0, "NW_OUTLIER", 1440, 1800, 41.2)
        .setExplanation("his income came mostly from jungle creeps");

    SectionView view = section.view(input(0L)).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Net worth outliers");
    assertThat(view.verdict())
        .isEqualTo("1 player on your team held an unusual share of the team's net worth");
    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(String.join("\n", section.lines(input(0L))))
        .isEqualTo(
            "**Net worth outliers**\n"
                + "Sven held 41% of the net worth of Radiant (your team) from 24:00 (his income"
                + " came mostly from jungle creeps)");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(input(0L)));
  }

  @Test
  void view_isRedForAFeederOnTheTrackedTeam() {
    outlier(0, "FEEDER", 600, 900, 7.0);

    SectionView mine = section.view(input(0L)).orElseThrow();
    SectionView nobody = section.view(input()).orElseThrow();

    assertThat(mine.accent()).isEqualTo(Accent.LOSS);
    assertThat(nobody.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(nobody.verdict())
        .isEqualTo("1 player held an unusual share of the team's net worth");
  }

  @Test
  void view_isEmptyWithoutUsableRows() {
    assertThat(section.view(input())).isEmpty();
  }
}
