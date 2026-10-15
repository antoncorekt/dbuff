package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MidActivityDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.MidActivityRepo;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MidActivitySectionTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Puck", 128L, "Lina");

  private final MidActivityRepo repo = mock(MidActivityRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final MidActivitySection section =
      new MidActivitySection(repo, heroNames, new ReplayReportProperties());

  private static MidActivityDomain.MidActivityDomainBuilder puck() {
    return MidActivityDomain.builder()
        .id(1L)
        .matchId(1L)
        .playerSlot(0L)
        .team("RADIANT")
        .level6Seconds(430)
        .nextAction("ROTATED")
        .nextPlace("TOP")
        .leaveSeconds(460)
        .killsAfterLeaving(2)
        .fightsJoined(4)
        .fightsTotal(9)
        .goldPerMinute(590)
        .enemyGoldPerMinute(500)
        .xpPerMinute(640)
        .enemyXpPerMinute(670)
        .verdict("ACTIVE");
  }

  private static MidActivityDomain.MidActivityDomainBuilder lina() {
    return MidActivityDomain.builder()
        .id(2L)
        .matchId(1L)
        .playerSlot(128L)
        .team("DIRE")
        .level6Seconds(500)
        .nextAction("STAYED_MID")
        .fightsJoined(2)
        .fightsTotal(9)
        .goldPerMinute(480)
        .enemyGoldPerMinute(590)
        .xpPerMinute(500)
        .enemyXpPerMinute(640)
        .verdict("PASSIVE");
  }

  private ReplayReportInput input(Boolean radiantWin, Long... focus) {
    when(repo.findByMatchId(1L)).thenReturn(List.of(puck().build(), lina().build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);
    MatchDomain match = MatchDomain.builder().id(1L).radiantWin(radiantWin).build();
    return new ReplayReportInput(1L, match, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultForEveryModeAtStandardDetail() {
    assertThat(section.name()).isEqualTo("mid-activity");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
  }

  @Test
  void tellsEachMidHeroInPlainWordsWithTheTeamResult() {
    assertThat(section.lines(input(true)))
        .containsExactly(
            "Puck (Radiant) was active: reached level 6 at 7:10, went to the top lane and made"
                + " 2 kills; joined 4 of 9 fights; his gold per minute was 18% above the enemy mid"
                + " and his experience per minute 4% below it. Radiant won.",
            "Lina (Dire) was passive: reached level 6 at 8:20 and stayed in the mid lane; joined 2"
                + " of 9 fights; his gold per minute was 19% below the enemy mid and his experience"
                + " per minute 22% below it. Dire lost.");
  }

  @Test
  void yourTeamComesFirstAndIsMarked() {
    List<String> lines = section.lines(input(true, 128L));

    assertThat(lines.getFirst()).startsWith("Lina (Dire, your team) was passive:");
    assertThat(lines.getLast()).startsWith("Puck (Radiant) was active:");
  }

  @Test
  void theViewLeadsWithTheVerdictAndTakesTheColourOfTheTrackedTeamsResult() {
    SectionView view = section.view(input(true, 0L)).orElseThrow();

    assertThat(view.title()).isEqualTo("Mid hero in the mid game");
    assertThat(view.verdict()).startsWith("Puck (Radiant, your team) was active:");
    assertThat(view.accent()).isEqualTo(Accent.WIN);
    assertThat(view.bullets()).hasSize(1);
    assertThat(section.view(input(true, 128L)).orElseThrow().accent()).isEqualTo(Accent.LOSS);
    assertThat(section.view(input(true)).orElseThrow().accent()).isEqualTo(Accent.NEUTRAL);
  }

  @Test
  void anUnknownResultIsLeftOut() {
    assertThat(section.lines(input(null)).getFirst()).endsWith("below it.");
  }

  @Test
  void aHeroWhoNeverReachedLevelSixSaysSo() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                lina()
                    .level6Seconds(null)
                    .maxLevel(5)
                    .nextAction(null)
                    .goldPerMinute(null)
                    .build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)).getFirst())
        .isEqualTo(
            "Lina (Dire) was passive: did not reach level 6; joined 2 of 9 fights; his experience"
                + " per minute was 22% below the enemy mid.");
  }

  @Test
  void withoutAnEnemyMidTheTeamAverageIsTheYardstick() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                puck()
                    .enemyGoldPerMinute(null)
                    .enemyXpPerMinute(null)
                    .teamGoldPerMinute(500)
                    .teamXpPerMinute(640)
                    .build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)).getFirst())
        .contains(
            "his gold per minute was 18% above his team's average and his experience per minute"
                + " level with it");
  }

  @Test
  void noRowsMeanNoLinesAndNoView() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());
    ReplayReportInput input = new ReplayReportInput(1L, null, Set.of(), Map.of(), 3);

    assertThat(section.lines(input)).isEmpty();
    assertThat(section.view(input)).isEmpty();
  }

  @Test
  void aHeroWithNoTrackHasAnUnknownLevelSixTime() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(lina().level6Seconds(null).maxLevel(null).nextAction(null).build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)).getFirst())
        .contains("level 6 time unknown")
        .doesNotContain("did not reach");
  }

  @Test
  void anUnknownVerdictSaysThereIsNotEnoughData() {
    when(repo.findByMatchId(1L)).thenReturn(List.of(lina().verdict("UNKNOWN").build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)).getFirst())
        .startsWith("Lina (Dire) (not enough data for a verdict): ");
  }

  @Test
  void eachFigureFallsBackToTheTeamAverageOnItsOwnAndSaysSo() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(puck().enemyXpPerMinute(null).teamXpPerMinute(800).build()));
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3)).getFirst())
        .contains(
            "his gold per minute was 18% above the enemy mid and his experience per minute"
                + " 20% below his team's average");
  }
}
