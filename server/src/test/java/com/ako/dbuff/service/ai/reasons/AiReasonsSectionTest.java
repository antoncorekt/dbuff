package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.ai.reasons.ReasonsResult.Status;
import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReasonsSectionTest {
  private final AiReasonsService service = mock(AiReasonsService.class);
  private final AiReasonsSection section = new AiReasonsSection(service, new PlainViewRenderer());

  private static Reason reason(int rank, String evidence) {
    return new Reason(rank, "Title " + rank, "Because " + rank + ".", List.of(evidence));
  }

  @Test
  void itIsAStandardSectionAfterTheOthers_andOffByDefault() {
    assertThat(section.name()).isEqualTo("ai-reasons");
    assertThat(section.order()).isEqualTo(80);
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(section.defaultEnabled()).isFalse();

    ReplayReportProperties properties = new ReplayReportProperties();
    assertThat(
            new SectionSwitch(properties, List.<ReplayReportSection>of(section)).isEnabled(section))
        .isFalse();
  }

  @Test
  void itsOwnSwitchTurnsItOnThroughTheEnvironmentStyleName() {
    ReplayReportProperties properties = new ReplayReportProperties();
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(true);
    properties.getSections().put("aireasons", toggle);

    assertThat(
            new SectionSwitch(properties, List.<ReplayReportSection>of(section)).isEnabled(section))
        .isTrue();
  }

  @Test
  void aWonMatchGivesAGreenCardWithNumberedBulletsAndPlainEvidenceLabels() {
    when(service.reasons(ReasonsFixtures.input()))
        .thenReturn(
            new ReasonsResult(
                Status.READY,
                List.of(
                    reason(1, "F3"),
                    reason(2, "LANE_1"),
                    reason(3, "RESULT"),
                    reason(4, "VISION_2"),
                    reason(5, "ENDGAME_1")),
                "gpt-test"));

    SectionView view = section.view(ReasonsFixtures.input()).orElseThrow();

    assertThat(view.title()).isEqualTo("Top 5 reasons");
    assertThat(view.verdict()).isEqualTo("Dire won; your team won");
    assertThat(view.accent()).isEqualTo(Accent.WIN);
    assertThat(view.bullets()).hasSize(5);
    assertThat(view.bullets().get(0))
        .isEqualTo("1. Title 1: Because 1. (based on: important fights)");
    assertThat(view.bullets().get(1)).endsWith("(based on: lanes)");
    assertThat(view.footnote()).doesNotContain("Only").contains("Written by gpt-test");
    assertThat(String.join("\n", view.bullets())).doesNotContain("F3").doesNotContain("LANE_1");
  }

  @Test
  void aLostMatchIsRedAndFewerThanFiveSaysSo() {
    var lost =
        new com.ako.dbuff.service.replay.report.ReplayReportInput(
            ReasonsFixtures.MATCH_ID,
            ReasonsFixtures.match(),
            java.util.Set.of(0L),
            java.util.Map.of(),
            3);
    when(service.reasons(lost))
        .thenReturn(
            new ReasonsResult(Status.CACHED, List.of(reason(1, "F1"), reason(2, "F1")), "m2"));

    SectionView view = section.view(lost).orElseThrow();

    assertThat(view.verdict()).isEqualTo("Dire won; your team lost");
    assertThat(view.accent()).isEqualTo(Accent.LOSS);
    assertThat(view.footnote()).startsWith("Only 2 reasons had enough supporting data.");
  }

  @Test
  void anAnswerWithNoReasonsShowsANotEnoughDataCard() {
    when(service.reasons(ReasonsFixtures.input()))
        .thenReturn(new ReasonsResult(Status.READY, List.of(), "m"));

    SectionView view = section.view(ReasonsFixtures.input()).orElseThrow();

    assertThat(view.bullets()).containsExactly("Not enough data to explain this match.");
    assertThat(view.footnote()).startsWith("Written by m");
  }

  @Test
  void theModelAPersonAskedForTravelsInTheInputAndIsNamedInTheFootnote() {
    var input = ReasonsFixtures.input().withModelOverride("big");
    when(service.reasons(input))
        .thenReturn(new ReasonsResult(Status.READY, List.of(reason(1, "F1")), "big"));

    assertThat(section.view(input).orElseThrow().footnote()).contains("Written by big");
  }

  @Test
  void anUnknownResultIsNeutral() {
    var unknown =
        new com.ako.dbuff.service.replay.report.ReplayReportInput(
            1L,
            com.ako.dbuff.dao.model.MatchDomain.builder().id(1L).build(),
            java.util.Set.of(0L),
            java.util.Map.of(),
            3);
    when(service.reasons(unknown))
        .thenReturn(new ReasonsResult(Status.READY, List.of(reason(1, "F1")), "m"));

    SectionView view = section.view(unknown).orElseThrow();

    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(view.verdict()).isNull();
  }

  @Test
  void withoutReasonsTheSectionSaysNothing() {
    for (Status status :
        List.of(Status.SWITCHED_OFF, Status.NO_API_KEY, Status.CALL_LIMIT, Status.FAILED)) {
      when(service.reasons(ReasonsFixtures.input())).thenReturn(ReasonsResult.of(status));

      assertThat(section.view(ReasonsFixtures.input())).isEqualTo(Optional.empty());
      assertThat(section.lines(ReasonsFixtures.input())).isEmpty();
    }
  }
}
