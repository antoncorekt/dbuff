package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.ReportDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReasonsCommandHandlerTest {
  private static final long MATCH = 77L;

  private final AiReasonsService service = mock(AiReasonsService.class);
  private final ReplayReportService reports = mock(ReplayReportService.class);
  private final AiReasonsCommandHandler handler = new AiReasonsCommandHandler(service, reports);
  private final DbufInstanceConfigDomain config = new DbufInstanceConfigDomain();

  @BeforeEach
  void setUp() {
    config.setId("i1");
    when(service.availability()).thenReturn(AiReasonsAvailability.AVAILABLE);
    when(reports.hasStoredAnalysis(MATCH)).thenReturn(true);
  }

  private static FakeCommandContext context() {
    return FakeCommandContext.builder().threadName("Match " + MATCH).build();
  }

  @Test
  void switchedOff_refusesInOneLineBeforeAcknowledging() {
    when(service.availability()).thenReturn(AiReasonsAvailability.SWITCHED_OFF);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("switched off");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(reports, never()).deliverSection(MATCH, "i1", "ai-reasons");
  }

  @Test
  void noApiKey_refusesInOneLine() {
    when(service.availability()).thenReturn(AiReasonsAvailability.NO_API_KEY);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies()).singleElement().asString().contains("API key");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void noStoredRun_refusesInOneLine() {
    when(reports.hasStoredAnalysis(MATCH)).thenReturn(false);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies())
        .singleElement()
        .asString()
        .contains("no stored replay analysis");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(reports, never()).deliverSection(MATCH, "i1", "ai-reasons");
  }

  @Test
  void success_acknowledgesAndPostsTheSectionCard() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons")).thenReturn(ReportDelivery.POSTED);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getAcknowledgeSummary()).contains("top reasons").contains("77");
    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getPosts()).isEmpty();
    assertThat(context.getFailures()).isEmpty();
  }

  @Test
  void nothingToPost_saysWhyInOneLine() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons"))
        .thenReturn(ReportDelivery.SECTION_EMPTY);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts()).singleElement().asString().contains("not posted");
  }

  @Test
  void anUnexpectedFailureIsReportedInTheThread() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons"))
        .thenThrow(new IllegalStateException("x"));
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getFailures()).hasSize(1);
  }
}
