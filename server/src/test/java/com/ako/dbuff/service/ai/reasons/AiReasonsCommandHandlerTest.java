package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.ReportDelivery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    when(service.allowedModels()).thenReturn(List.of("fake-model", "big"));
    when(service.isModelAllowed(any()))
        .thenAnswer(
            c ->
                c.getArgument(0) == null
                    || List.of("fake-model", "big").contains(((String) c.getArgument(0)).strip()));
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
    verify(reports, never()).deliverSection(MATCH, "i1", "ai-reasons", null);
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
    verify(reports, never()).deliverSection(MATCH, "i1", "ai-reasons", null);
  }

  @Test
  void success_acknowledgesAndPostsTheSectionCard() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons", null)).thenReturn(ReportDelivery.POSTED);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getAcknowledgeSummary()).contains("top reasons").contains("77");
    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getPosts()).isEmpty();
    assertThat(context.getFailures()).isEmpty();
  }

  @Test
  void nothingToPost_saysWhyInOneLine() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons", null))
        .thenReturn(ReportDelivery.SECTION_EMPTY);
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts()).singleElement().asString().contains("not posted");
  }

  @Test
  void aModelOptionOutsideTheAllowListIsRefusedBeforeAcknowledging() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .threadName("Match " + MATCH)
            .option("model", "gpt-expensive")
            .build();

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies())
        .singleElement()
        .asString()
        .contains("`gpt-expensive` is not allowed")
        .contains("fake-model, big");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(reports, never())
        .deliverSection(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
  }

  @Test
  void anAllowedModelOptionIsPassedOnForThatPostOnly() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons", " big "))
        .thenReturn(ReportDelivery.POSTED);
    FakeCommandContext context =
        FakeCommandContext.builder().threadName("Match " + MATCH).option("model", " big ").build();

    handler.handle(context, MATCH, config);

    verify(reports).deliverSection(MATCH, "i1", "ai-reasons", " big ");
    assertThat(context.getAcknowledgeSummary()).isNotNull();
  }

  @Test
  void anUnexpectedFailureIsReportedInTheThread() {
    when(reports.deliverSection(MATCH, "i1", "ai-reasons", null))
        .thenThrow(new IllegalStateException("x"));
    FakeCommandContext context = context();

    handler.handle(context, MATCH, config);

    assertThat(context.getFailures()).hasSize(1);
  }
}
