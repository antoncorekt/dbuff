package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisionSectionTest {
  private final FightVisionRepo repo = mock(FightVisionRepo.class);
  private final FightRepo fightRepo = mock(FightRepo.class);
  private final FightImportanceRepo importanceRepo = mock(FightImportanceRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final VisionSection section =
      new VisionSection(repo, fightRepo, importanceRepo, heroNames, properties);
  private final List<FightVisionDomain> rows = new ArrayList<>();
  private final List<FightDomain> fights = new ArrayList<>();
  private final List<FightImportanceDomain> importance = new ArrayList<>();
  private long nextId = 1;

  VisionSectionTest() {
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(fightRepo.findByMatchId(1L)).thenReturn(fights);
    when(importanceRepo.findByMatchId(1L)).thenReturn(importance);
    when(heroNames.bySlot(1L)).thenReturn(Map.of(0L, "Axe", 128L, "Riki", 129L, "Lion"));
  }

  private FightVisionDomain row(int index, int start, String team, String verdict) {
    if (fights.stream().noneMatch(f -> f.getFightIndex() == index)) {
      fights.add(
          FightDomain.builder()
              .id(nextId++)
              .fightIndex(index)
              .startSeconds((double) start)
              .build());
    }
    FightVisionDomain row =
        FightVisionDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .fightIndex(index)
            .team(team)
            .verdict(verdict)
            .activeObservers(0)
            .build();
    rows.add(row);
    return row;
  }

  private void swing(int index, long gold) {
    importance.add(FightImportanceDomain.builder().fightIndex(index).goldChange(gold).build());
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultAndStandardDetail() {
    assertThat(section.name()).isEqualTo("vision");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(com.ako.dbuff.dao.model.ReportDetail.STANDARD);
  }

  @Test
  void postsNothingUntilItsOwnSwitchIsOn() {
    SectionSwitch off = new SectionSwitch(properties, List.of(section));
    assertThat(off.isEnabled(section)).isFalse();

    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(true);
    properties.getSections().put("vision", toggle);
    assertThat(off.isEnabled(section)).isTrue();
  }

  @Test
  void writesALineForEachKindOfProblemAndASummaryFirst() {
    row(1, 1663, "DIRE", "NO_VISION");
    FightVisionDomain invisible = row(2, 1800, "RADIANT", "INVISIBLE_HERO_NO_DETECTION");
    invisible.setInvisibleHeroes("128");
    FightVisionDomain late = row(3, 2000, "DIRE", "LATE_WARD");
    late.setActiveObservers(1);
    late.setLateWardSeconds(20);
    row(3, 2000, "RADIANT", "FINE");

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly(
            "**Vision**",
            "Fights where a team had no vision: 1 of 3 important fights (Dire 1)",
            "27:43 Dire fought without vision: no ward of theirs covered the fight.",
            "30:00 Radiant had no sentry or dust against Riki.",
            "33:20 Dire's only ward was placed 20 seconds before the fight.");
  }

  @Test
  void severalProblemsOfOneTeamJoinIntoOneLine() {
    FightVisionDomain r = row(1, 600, "RADIANT", "NO_VISION,INVISIBLE_HERO_NO_DETECTION");
    r.setInvisibleHeroes("128,129");

    assertThat(section.lines(input()).get(2))
        .isEqualTo(
            "10:00 Radiant fought without vision: no ward of theirs covered the fight, and they"
                + " had no sentry or dust against Riki and Lion.");
  }

  @Test
  void manyLateWardsAndAnUnknownHeroOrSecondsStillRead() {
    FightVisionDomain late = row(1, 600, "DIRE", "LATE_WARD");
    late.setActiveObservers(2);
    late.setLateWardSeconds(null);
    row(2, 900, "DIRE", "INVISIBLE_HERO_NO_DETECTION").setInvisibleHeroes("?");

    List<String> lines = section.lines(input());

    assertThat(lines.get(2))
        .isEqualTo(
            "10:00 Dire's wards were all placed shortly before the fight, the latest just"
                + " before the fight.");
    assertThat(lines.get(3))
        .isEqualTo("15:00 Dire had no sentry or dust against an invisible hero.");
  }

  @Test
  void prefersTheTrackedTeamAndFallsBackToBothWhenItHasNoProblem() {
    row(1, 600, "RADIANT", "NO_VISION");
    row(1, 600, "DIRE", "NO_VISION");

    List<String> yours = section.lines(input(0L));
    assertThat(yours).hasSize(3);
    assertThat(yours.get(2)).contains("Radiant");
    assertThat(yours.get(1)).contains("(Dire 1, Radiant 1)");

    rows.clear();
    row(2, 900, "DIRE", "NO_VISION");
    row(2, 900, "RADIANT", "FINE");
    assertThat(section.lines(input(0L))).hasSize(3).anyMatch(l -> l.contains("Dire fought"));
    assertThat(section.lines(input(0L, 128L))).hasSize(3);
  }

  @Test
  void capsByTheSwingKeepsTimeOrderAndCountsTheLeftOut() {
    properties.setMaxVisionLines(2);
    row(1, 100, "DIRE", "NO_VISION");
    row(2, 200, "DIRE", "NO_VISION");
    row(3, 300, "DIRE", "NO_VISION");
    row(4, 400, "DIRE", "NO_VISION");
    swing(1, 5000);
    swing(2, 100);
    swing(3, 4000);
    swing(4, 50);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(5);
    assertThat(lines.get(2)).startsWith("1:40 ");
    assertThat(lines.get(3)).startsWith("5:00 ");
    assertThat(lines.get(4)).isEqualTo("and 2 more vision problems");
  }

  @Test
  void withoutASwingTheLatestFightsWinAndOneLeftOutIsSingular() {
    properties.setMaxVisionLines(1);
    row(1, 100, "DIRE", "NO_VISION");
    row(2, 200, "DIRE", "NO_VISION");

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).startsWith("3:20 ");
    assertThat(lines.get(3)).isEqualTo("and 1 more vision problem");
  }

  @Test
  void aTieBreaksOnTheRowIdWhateverTheRepositoryOrder() {
    properties.setMaxVisionLines(1);
    row(1, 100, "DIRE", "NO_VISION");
    row(1, 100, "RADIANT", "LATE_WARD").setActiveObservers(1);
    List<FightVisionDomain> reversed = new ArrayList<>(rows);
    Collections.reverse(reversed);
    when(repo.findByMatchId(1L)).thenReturn(reversed);

    assertThat(section.lines(input()).get(2)).contains("Dire fought");
  }

  @Test
  void aRepeatedRowOfAFightAndTeamCountsOnceAndTheNewestWins() {
    row(1, 100, "DIRE", "NO_VISION");
    row(1, 100, "DIRE", "FINE");

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void printsNothingWithoutProblemRowsAndDoesNotThrowOnNullData() {
    assertThat(section.lines(input())).isEmpty();
    row(1, 100, "DIRE", "FINE");
    row(1, 100, "RADIANT", "SOMETHING_NEW");
    row(2, 200, "RADIANT", null);
    assertThat(section.lines(input())).isEmpty();

    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();

    rows.clear();
    rows.add(FightVisionDomain.builder().verdict("NO_VISION").build());
    rows.add(null);
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(fightRepo.findByMatchId(1L)).thenReturn(null);
    when(importanceRepo.findByMatchId(1L)).thenReturn(null);

    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(2))
        .isEqualTo("? An unknown team fought without vision: no ward of theirs covered the fight.");
  }

  @Test
  void validatesTheLineCap() {
    for (int bad : new int[] {0, 11}) {
      properties.setMaxVisionLines(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-vision-lines");
    }
    properties.setMaxVisionLines(10);
    properties.validate();
    assertThat(new ReplayReportProperties().getMaxVisionLines()).isEqualTo(4);
  }
}
