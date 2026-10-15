package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
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
    when(heroNames.iconBySlot(1L)).thenReturn(Map.of(0L, "Axe", 128L, "Riki", 129L, "Lion"));
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
  void noVisionIsAProblemOnlyAgainstATeamThatHadVision() {
    row(1, 1663, "DIRE", "NO_VISION");
    row(1, 1663, "RADIANT", "FINE");
    FightVisionDomain invisible = row(2, 1800, "RADIANT", "INVISIBLE_HERO_NO_DETECTION");
    invisible.setInvisibleHeroes("128");
    row(2, 1800, "DIRE", "FINE");
    FightVisionDomain late = row(3, 2000, "DIRE", "LATE_WARD");
    late.setActiveObservers(1);
    late.setActiveSentries(0);
    late.setLateWardSeconds(20);
    row(3, 2000, "RADIANT", "FINE");

    assertThat(section.lines(input()))
        .containsExactly(
            "**Vision**",
            "Fights where only one team had vision: 1 of 3 important fights",
            "27:43 Dire fought without vision: no ward of theirs covered the fight.",
            "30:00 Radiant had no sentry or dust against Riki.",
            "33:20 Dire's only ward was placed 20 seconds before the fight.");
  }

  @Test
  void anOpponentWithOnlySentriesCoveringCountsAsHavingVision() {
    row(1, 600, "DIRE", "NO_VISION");
    FightVisionDomain other = row(1, 600, "RADIANT", "NO_VISION");
    other.setActiveSentries(1);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(2)).contains("Dire fought without vision");
  }

  @Test
  void whenNeitherTeamHadWardsThereIsNoLineAndTheSentenceOnlyWhenItIsMostFights() {
    row(1, 600, "DIRE", "NO_VISION");
    row(1, 600, "RADIANT", "NO_VISION");
    row(2, 900, "DIRE", "NO_VISION");
    row(2, 900, "RADIANT", "NO_VISION");
    row(3, 1200, "DIRE", "NO_VISION");
    row(3, 1200, "RADIANT", "FINE");

    assertThat(section.lines(input()))
        .containsExactly(
            "**Vision**",
            "Fights where only one team had vision: 1 of 3 important fights",
            "Neither team had ward coverage in 2 of 3 important fights",
            "20:00 Dire fought without vision: no ward of theirs covered the fight.");

    rows.removeIf(r -> r.getFightIndex() == 2);
    assertThat(section.lines(input())).noneMatch(l -> l.startsWith("Neither"));
  }

  @Test
  void aMatchWhereNeitherTeamEverHadWardsPrintsNothing() {
    row(1, 600, "DIRE", "NO_VISION");
    row(1, 600, "RADIANT", "NO_VISION");

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void aRowWithoutItsOpponentIsNotAContrast() {
    row(1, 600, "DIRE", "NO_VISION");

    assertThat(section.lines(input())).isEmpty();
  }

  @Test
  void severalProblemsOfOneTeamJoinIntoOneSentence() {
    FightVisionDomain r = row(1, 600, "RADIANT", "NO_VISION,INVISIBLE_HERO_NO_DETECTION");
    r.setInvisibleHeroes("128,129");
    row(1, 600, "DIRE", "FINE");

    assertThat(section.lines(input()).get(2))
        .isEqualTo(
            "10:00 Radiant fought without vision: no ward of theirs covered the fight, and they"
                + " had no sentry or dust against Riki and Lion.");
  }

  @Test
  void noVisionAndLateWardKeepBothFactsInOneSentence() {
    FightVisionDomain r = row(1, 600, "RADIANT", "NO_VISION,LATE_WARD");
    r.setActiveObservers(1);
    r.setActiveSentries(0);
    r.setLateWardSeconds(5);
    row(1, 600, "DIRE", "FINE");

    assertThat(section.lines(input()).get(2))
        .isEqualTo(
            "10:00 Radiant fought without vision: no ward of theirs covered the fight; Radiant's"
                + " only ward was placed 5 seconds before the fight.");
  }

  @Test
  void aSingularSecondAndAnUnknownNumberOfSecondsReadWell() {
    FightVisionDomain one = row(1, 600, "DIRE", "LATE_WARD");
    one.setActiveObservers(1);
    one.setActiveSentries(0);
    one.setLateWardSeconds(1);
    FightVisionDomain unknown = row(2, 900, "DIRE", "LATE_WARD");
    unknown.setActiveObservers(2);
    unknown.setActiveSentries(0);
    FightVisionDomain several = row(3, 1200, "DIRE", "LATE_WARD");
    several.setActiveObservers(2);
    several.setActiveSentries(0);
    several.setLateWardSeconds(8);
    properties.setMaxVisionLines(5);

    List<String> lines = section.lines(input());

    assertThat(lines.get(1))
        .isEqualTo("10:00 Dire's only ward was placed 1 second before the fight.");
    assertThat(lines.get(2))
        .isEqualTo("15:00 Dire's observers were all placed shortly before the fight.");
    assertThat(lines.get(3))
        .isEqualTo(
            "20:00 Dire's observers were all placed shortly before the fight, the latest 8 seconds"
                + " before the fight.");
  }

  @Test
  void onlyWardCountsSentriesAndNeverClaimsOnlyForUnknownCounts() {
    FightVisionDomain withSentry = row(1, 600, "DIRE", "LATE_WARD");
    withSentry.setActiveObservers(1);
    withSentry.setActiveSentries(1);
    withSentry.setLateWardSeconds(10);
    FightVisionDomain unknown = row(2, 900, "DIRE", "LATE_WARD");
    unknown.setActiveObservers(null);
    unknown.setActiveSentries(null);
    unknown.setLateWardSeconds(10);
    FightVisionDomain noSentryCount = row(3, 1200, "DIRE", "LATE_WARD");
    noSentryCount.setActiveObservers(1);
    noSentryCount.setActiveSentries(null);
    noSentryCount.setLateWardSeconds(10);
    properties.setMaxVisionLines(5);

    List<String> lines = section.lines(input());

    assertThat(lines.get(1))
        .isEqualTo("10:00 Dire's only observer was placed 10 seconds before the fight.");
    assertThat(lines.get(2))
        .isEqualTo("15:00 Dire's latest ward was placed 10 seconds before the fight.");
    assertThat(lines.get(3))
        .isEqualTo("20:00 Dire's latest ward was placed 10 seconds before the fight.");
  }

  @Test
  void anUnreadableInvisibleHeroIsNamedAsAnInvisibleHero() {
    row(1, 900, "DIRE", "INVISIBLE_HERO_NO_DETECTION").setInvisibleHeroes("?");

    assertThat(section.lines(input()).get(1))
        .isEqualTo("15:00 Dire had no sentry or dust against an invisible hero.");
  }

  @Test
  void theTrackedTeamIsPreferredAndTheViewSaysSo() {
    row(1, 600, "RADIANT", "NO_VISION");
    row(1, 600, "DIRE", "FINE");
    row(2, 900, "DIRE", "NO_VISION");
    row(2, 900, "RADIANT", "FINE");

    List<String> yours = section.lines(input(0L));

    assertThat(yours)
        .containsExactly(
            "**Vision**",
            "Fights where only one team had vision: 1 of 2 important fights for your team",
            "10:00 Radiant fought without vision: no ward of theirs covered the fight.");
    assertThat(section.lines(input()))
        .contains(
            "Fights where only one team had vision: 2 of 2 important fights (Dire 1, Radiant 1)");
  }

  @Test
  void aTrackedTeamWithoutProblemsFallsBackToBothAndSaysSo() {
    row(2, 900, "DIRE", "NO_VISION");
    row(2, 900, "RADIANT", "FINE");
    properties.setMaxVisionLines(1);
    row(3, 1200, "DIRE", "NO_VISION");
    row(3, 1200, "RADIANT", "FINE");
    swing(2, 100);
    swing(3, 50);

    List<String> lines = section.lines(input(0L));

    assertThat(lines)
        .containsExactly(
            "**Vision**",
            "Fights where only one team had vision: 2 of 2 important fights for both teams",
            "15:00 Dire fought without vision: no ward of theirs covered the fight.",
            "and 1 more vision problem for both teams");
  }

  @Test
  void capsByTheAbsoluteSwingKeepsTimeOrderAndCountsTheLeftOutInTheSameScope() {
    properties.setMaxVisionLines(2);
    for (int i = 1; i <= 4; i++) {
      row(i, i * 100, "DIRE", "NO_VISION");
      row(i, i * 100, "RADIANT", "FINE");
    }
    row(5, 500, "RADIANT", "NO_VISION");
    row(5, 500, "DIRE", "FINE");
    swing(1, -5000);
    swing(2, 100);
    swing(3, 4000);
    swing(4, 50);
    swing(5, 9000);

    List<String> lines = section.lines(input(128L));

    assertThat(lines).hasSize(5);
    assertThat(lines.get(1)).contains("4 of 5").doesNotContain("Radiant");
    assertThat(lines.get(2)).startsWith("1:40 ");
    assertThat(lines.get(3)).startsWith("5:00 ");
    assertThat(lines.get(4)).isEqualTo("and 2 more vision problems for your team");
  }

  @Test
  void theTrackedTeamsRowWinsATieOnTheSwing() {
    properties.setMaxVisionLines(1);
    row(1, 600, "RADIANT", "INVISIBLE_HERO_NO_DETECTION");
    row(1, 600, "DIRE", "INVISIBLE_HERO_NO_DETECTION");
    swing(1, 100);

    assertThat(section.lines(input(128L)).get(1)).contains("Dire had");
    assertThat(section.lines(input(0L)).get(1)).contains("Radiant had");
  }

  @Test
  void theWorseProblemWinsATieOnSwingAndTeam() {
    properties.setMaxVisionLines(1);
    row(3, 800, "DIRE", "NO_VISION,INVISIBLE_HERO_NO_DETECTION");
    row(3, 800, "RADIANT", "FINE");
    row(4, 800, "DIRE", "LATE_WARD").setActiveObservers(1);
    swing(3, 100);
    swing(4, -100);

    assertThat(section.lines(input()).get(2)).contains("fought without vision");
  }

  @Test
  void aTieOnEverythingBreaksOnTheRowIdWhateverTheRepositoryOrder() {
    properties.setMaxVisionLines(1);
    row(1, 100, "DIRE", "INVISIBLE_HERO_NO_DETECTION");
    row(1, 100, "RADIANT", "INVISIBLE_HERO_NO_DETECTION");
    swing(1, 10);
    List<FightVisionDomain> reversed = new ArrayList<>(rows);
    Collections.reverse(reversed);
    when(repo.findByMatchId(1L)).thenReturn(reversed);
    List<FightDomain> reversedFights = new ArrayList<>(fights);
    Collections.reverse(reversedFights);
    fights.add(FightDomain.builder().id(999L).fightIndex(1).startSeconds(100.0).build());
    when(fightRepo.findByMatchId(1L)).thenReturn(reversedFights);

    assertThat(section.lines(input()).get(1)).contains("Dire had");
  }

  @Test
  void theLatestRowOfAFightAndTeamWinsAndTheLatestFightRowToo() {
    row(1, 100, "DIRE", "NO_VISION");
    row(1, 100, "DIRE", "FINE");
    row(1, 100, "RADIANT", "FINE");

    assertThat(section.lines(input())).isEmpty();

    rows.clear();
    row(2, 100, "DIRE", "NO_VISION");
    row(2, 100, "RADIANT", "FINE");
    fights.add(FightDomain.builder().id(0L).fightIndex(2).startSeconds(5.0).build());
    assertThat(section.lines(input()).get(2)).startsWith("1:40 ");
  }

  @Test
  void withoutASwingTheLatestFightsWinAndOneLeftOutIsSingular() {
    properties.setMaxVisionLines(1);
    for (int i = 1; i <= 2; i++) {
      row(i, i * 100, "DIRE", "NO_VISION");
      row(i, i * 100, "RADIANT", "FINE");
    }

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).startsWith("3:20 ");
    assertThat(lines.get(3)).isEqualTo("and 1 more vision problem");
  }

  @Test
  void aLineIsCutAtAWordBoundaryAtTheBehaviourLineLength() {
    properties.setMaxBehaviourLineLength(60);
    row(1, 600, "RADIANT", "NO_VISION");
    row(1, 600, "DIRE", "FINE");

    String line = section.lines(input()).get(2);

    assertThat(line).hasSizeLessThanOrEqualTo(60).endsWith("...");
    assertThat(line).startsWith("10:00 Radiant fought without vision: no ward of");
  }

  @Test
  void rowsWithoutAFightIndexOrATeamAreSkippedAndDoNotCountInTheTotal() {
    row(1, 600, "DIRE", "NO_VISION");
    row(1, 600, "RADIANT", "FINE");
    rows.add(FightVisionDomain.builder().id(50L).team("DIRE").verdict("NO_VISION").build());
    rows.add(
        FightVisionDomain.builder().id(51L).fightIndex(7).team(null).verdict("NO_VISION").build());
    rows.add(
        FightVisionDomain.builder()
            .id(52L)
            .fightIndex(8)
            .team("NOBODY")
            .verdict("NO_VISION")
            .build());

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1))
        .isEqualTo("Fights where only one team had vision: 1 of 1 important fights");
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
    FightVisionDomain late =
        FightVisionDomain.builder().id(3L).fightIndex(1).team("DIRE").verdict("LATE_WARD").build();
    rows.add(late);
    rows.add(null);
    when(repo.findByMatchId(1L)).thenReturn(rows);
    when(fightRepo.findByMatchId(1L)).thenReturn(null);
    when(importanceRepo.findByMatchId(1L)).thenReturn(null);

    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines)
        .containsExactly("**Vision**", "? Dire's latest ward was placed just before the fight.");
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

  private void threeProblemFights() {
    row(1, 1663, "DIRE", "NO_VISION");
    row(1, 1663, "RADIANT", "FINE");
    FightVisionDomain invisible = row(2, 1800, "RADIANT", "INVISIBLE_HERO_NO_DETECTION");
    invisible.setInvisibleHeroes("128");
    row(2, 1800, "DIRE", "FINE");
    FightVisionDomain late = row(3, 2000, "DIRE", "LATE_WARD");
    late.setActiveObservers(1);
    late.setActiveSentries(0);
    late.setLateWardSeconds(20);
    row(3, 2000, "RADIANT", "FINE");
  }

  @Test
  void view_countsTheProblemsAndKeepsPlainTextAsItWas() {
    threeProblemFights();

    SectionView all = section.view(input()).orElseThrow();

    ViewRendering.assertWithinLimits(all);
    assertThat(all.title()).isEqualTo("Vision");
    assertThat(all.verdict()).isEqualTo("3 vision problems in important fights");
    assertThat(all.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(all.bullets()).hasSize(4);
    assertThat(String.join("\n", section.lines(input())))
        .isEqualTo(
            "**Vision**\n"
                + "Fights where only one team had vision: 1 of 3 important fights\n"
                + "27:43 Dire fought without vision: no ward of theirs covered the fight.\n"
                + "30:00 Radiant had no sentry or dust against Riki.\n"
                + "33:20 Dire's only ward was placed 20 seconds before the fight.");
    assertThat(new PlainViewRenderer().lines(all)).isEqualTo(section.lines(input()));
  }

  @Test
  void view_isRedWhenTheTrackedTeamHasProblemsAndGreenWhenOnlyTheOtherDoes() {
    threeProblemFights();

    SectionView dire = section.view(input(128L)).orElseThrow();

    assertThat(dire.verdict()).isEqualTo("2 vision problems for your team in important fights");
    assertThat(dire.accent()).isEqualTo(Accent.LOSS);

    rows.removeIf(r -> "DIRE".equals(r.getTeam()) && !"FINE".equals(r.getVerdict()));
    rows.add(
        FightVisionDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .fightIndex(9)
            .team("DIRE")
            .verdict("FINE")
            .activeObservers(1)
            .build());
    SectionView clean = section.view(input(128L)).orElseThrow();
    assertThat(clean.verdict())
        .isEqualTo("No vision problems for your team; 1 vision problem for the other team");
    assertThat(clean.accent()).isEqualTo(Accent.WIN);
  }

  @Test
  void view_isEmptyWithoutProblems() {
    row(1, 600, "DIRE", "NO_VISION");
    row(1, 600, "RADIANT", "NO_VISION");

    assertThat(section.view(input())).isEmpty();
  }

  @Test
  void view_countsEveryProblemEvenWhenTheCapLeavesSomeOut() {
    threeProblemFights();
    properties.setMaxVisionLines(1);

    SectionView view = section.view(input()).orElseThrow();

    assertThat(view.verdict()).isEqualTo("3 vision problems in important fights");
    assertThat(view.bullets()).anyMatch(b -> b.startsWith("and 2 more vision problems"));
  }
}
