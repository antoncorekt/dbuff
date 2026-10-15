package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EndGameSectionTest {
  private final EndGameSummaryRepo repo = mock(EndGameSummaryRepo.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final EndGameSection section = new EndGameSection(repo, properties);

  private static EndGameSummaryDomain.EndGameSummaryDomainBuilder won() {
    return EndGameSummaryDomain.builder()
        .id(1L)
        .matchId(1L)
        .winnerTeam("RADIANT")
        .durationSeconds(2480)
        .leadKnown(true)
        .lastLeadGold(-5000L)
        .peakLeadSeconds(1450)
        .peakLeadGold(8300L)
        .leadLost(true)
        .heldSeconds(300)
        .thrown(false)
        .turningFightIndex(4)
        .turningSeconds(1999.0)
        .turningKind("CONVERTED")
        .objectiveKind("TOWER")
        .objectiveKey("npc_dota_badguys_tower3_top")
        .radiantBuybacks(0)
        .direBuybacks(3)
        .buybackWindowSeconds(600);
  }

  private List<String> lines(EndGameSummaryDomain row, Long... focus) {
    when(repo.findByMatchId(1L)).thenReturn(row == null ? List.of() : List.of(row));
    return section.lines(new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3));
  }

  @Test
  void isOffByDefaultAndStandardDetail() {
    assertThat(section.name()).isEqualTo("endgame");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
  }

  @Test
  void tellsTheVerdictWithTheTurningPointAndTheBuybacks() {
    assertThat(lines(won().build()))
        .containsExactly(
            "Radiant won at 41:20 after Dire led by 8.3k at 24:10; the turning point was the won"
                + " fight at 33:19, which Radiant turned into the top lane tier 3 tower.",
            "Dire used 3 buybacks in the last 10 minutes, Radiant 0.");
  }

  @Test
  void aThrownLeadSaysSoAndMarksYourTeam() {
    assertThat(lines(won().thrown(true).build(), 128L).getFirst())
        .isEqualTo(
            "Dire (your team) threw a lead of 8.3k at 24:10 and lost at 41:20; the turning point"
                + " was the won fight at 33:19, which Radiant turned into the top lane tier 3"
                + " tower.");
  }

  @Test
  void aSwingTurningPointNamesTheGoldInsteadOfAnObjective() {
    EndGameSummaryDomain row =
        won()
            .turningKind("SWING")
            .objectiveKind(null)
            .objectiveKey(null)
            .turningGoldChange(3200L)
            .build();

    assertThat(lines(row).getFirst()).endsWith("which Radiant gained 3.2k.");
  }

  @Test
  void aLoserWhoNeverLedIsSaidSo() {
    EndGameSummaryDomain row =
        won().peakLeadSeconds(null).peakLeadGold(null).turningSeconds(null).leadLost(null).build();

    assertThat(lines(row).getFirst()).isEqualTo("Radiant won at 41:20; Dire never led.");
  }

  @Test
  void aLeadStillHeldAtTheLastMinuteHasItsOwnWordingWithoutThrow() {
    assertThat(lines(won().leadLost(false).thrown(false).lastLeadGold(5000L).build()).getFirst())
        .isEqualTo("Radiant won at 41:20 although Dire still led by 5k at the last minute.");
  }

  @Test
  void anUnknownLeadSaysOnlyWhoWon() {
    EndGameSummaryDomain row =
        won().leadKnown(false).peakLeadSeconds(null).peakLeadGold(null).build();

    assertThat(lines(row).getFirst()).isEqualTo("Radiant won at 41:20.");
    assertThat(lines(won().leadKnown(null).build()).getFirst()).isEqualTo("Radiant won at 41:20.");
  }

  @Test
  void noBuybacksMeansNoBuybackLine() {
    assertThat(lines(won().direBuybacks(0).build())).hasSize(1);
  }

  @Test
  void aSingleBuybackAndANonMinuteWindowReadCorrectly() {
    assertThat(
            lines(won().radiantBuybacks(1).direBuybacks(1).buybackWindowSeconds(90).build()).get(1))
        .isEqualTo("Dire used 1 buyback in the last 90 seconds, Radiant 1.");
  }

  @Test
  void aMissingRowOrUnknownWinnerPrintsNothing() {
    assertThat(lines(null)).isEmpty();
    assertThat(lines(won().winnerTeam(null).build())).isEmpty();
    when(repo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3))).isEmpty();
  }

  @Test
  void aSparseRowDoesNotFail() {
    EndGameSummaryDomain row = EndGameSummaryDomain.builder().id(1L).winnerTeam("DIRE").build();

    assertThat(lines(row)).containsExactly("Dire won.");
  }

  @Test
  void linesAreCutAtTheConfiguredLength() {
    properties.setMaxEndGameLineLength(60);

    assertThat(lines(won().build()))
        .allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(60));
  }

  @Test
  void theLineLengthIsValidatedLikeTheOthers() {
    for (int bad : new int[] {59, 601}) {
      properties.setMaxEndGameLineLength(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-end-game-line-length");
    }
    properties.setMaxEndGameLineLength(400);
    properties.validate();
  }

  private SectionView view(EndGameSummaryDomain row, Long... focus) {
    when(repo.findByMatchId(1L)).thenReturn(row == null ? List.of() : List.of(row));
    return section.view(new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3)).orElseThrow();
  }

  @Test
  void view_putsTheVerdictFirstAndColoursByTheTrackedTeamsResult() {
    SectionView lost = view(won().build(), 128L);
    SectionView win = view(won().build(), 0L);
    SectionView nobody = view(won().build());

    ViewRendering.assertWithinLimits(lost);
    assertThat(lost.title()).isEqualTo("How the game ended");
    assertThat(lost.verdict())
        .isEqualTo(
            "Radiant won at 41:20 after Dire (your team) led by 8.3k at 24:10; the turning point"
                + " was the won fight at 33:19, which Radiant turned into the top lane tier 3"
                + " tower.");
    assertThat(lost.bullets())
        .containsExactly("Dire (your team) used 3 buybacks in the last 10 minutes, Radiant 0.");
    assertThat(lost.accent()).isEqualTo(Accent.LOSS);
    assertThat(win.accent()).isEqualTo(Accent.WIN);
    assertThat(nobody.accent()).isEqualTo(Accent.NEUTRAL);
  }

  @Test
  void view_keepsPlainTextAsItWas() {
    List<String> lines = lines(won().build());
    SectionView view = view(won().build());

    assertThat(String.join("\n", lines))
        .isEqualTo(
            "Radiant won at 41:20 after Dire led by 8.3k at 24:10; the turning point was the won"
                + " fight at 33:19, which Radiant turned into the top lane tier 3 tower.\n"
                + "Dire used 3 buybacks in the last 10 minutes, Radiant 0.");
    assertThat(view.verdict()).isEqualTo(lines.get(0));
    assertThat(view.bullets()).containsExactly(lines.get(1));
  }

  @Test
  void view_isEmptyWithoutASummary() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3))).isEmpty();
  }
}
