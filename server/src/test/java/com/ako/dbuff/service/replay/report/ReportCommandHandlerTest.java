package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportCommandHandlerTest {
  private static final long MATCH = 77L;

  private final ReplayReportService reports = mock(ReplayReportService.class);
  private final OnDemandCooldown cooldown = mock(OnDemandCooldown.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch =
      new SectionSwitch(
          properties,
          List.of(
              TestSections.named("lanes", 20, false), TestSections.named("opendota", 200, false)));
  private final ReportCommandHandler handler =
      new ReportCommandHandler(reports, sectionSwitch, cooldown);
  private final DbufInstanceConfigDomain config = new DbufInstanceConfigDomain();

  @BeforeEach
  void setUp() {
    config.setId("i1");
    when(reports.hasStoredAnalysis(MATCH)).thenReturn(true);
    when(reports.deliverSection(anyLong(), anyString(), anyString()))
        .thenReturn(ReportDelivery.POSTED);
  }

  private static FakeCommandContext context(String section) {
    FakeCommandContext.Builder builder =
        FakeCommandContext.builder().threadName("Match " + MATCH).channelId("thread-1");
    return (section == null ? builder : builder.option("section", section)).build();
  }

  @Test
  void aSection_isPostedThroughDeliverSectionAfterAcknowledging() {
    FakeCommandContext context = context("Lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getAcknowledgeSummary()).contains("lanes");
    assertThat(context.getAcknowledgeThreadName()).isEqualTo("Match " + MATCH);
    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getPosts()).isEmpty();
    verify(reports).deliverSection(MATCH, "i1", "lanes");
  }

  @Test
  void openDotaIsJustASectionName() {
    FakeCommandContext context = context("opendota");

    handler.handle(context, MATCH, config);

    verify(reports).deliverSection(MATCH, "i1", "opendota");
  }

  @Test
  void all_postsWhatAutomaticDeliveryWouldThroughRedeliver() {
    when(reports.redeliver(MATCH, "i1")).thenReturn(ReportDelivery.POSTED);
    FakeCommandContext context = context("ALL");

    handler.handle(context, MATCH, config);

    verify(reports).redeliver(MATCH, "i1");
    verify(reports, never()).deliverSection(anyLong(), anyString(), anyString());
    assertThat(context.getPosts()).isEmpty();
  }

  @Test
  void all_withNoSectionsToPostSaysWhy() {
    when(reports.redeliver(MATCH, "i1")).thenReturn(ReportDelivery.NO_SECTIONS);
    FakeCommandContext context = context("all");

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts())
        .singleElement()
        .asString()
        .contains("Nothing is set to post automatically");
  }

  @Test
  void aSectionWithoutData_saysSoPlainly() {
    when(reports.deliverSection(MATCH, "i1", "lanes")).thenReturn(ReportDelivery.SECTION_EMPTY);
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts())
        .singleElement()
        .asString()
        .contains("`lanes`")
        .contains("nothing to show")
        .contains("data does not exist");
  }

  @Test
  void aFailedDelivery_isToldNotThrown() {
    when(reports.deliverSection(MATCH, "i1", "lanes")).thenReturn(ReportDelivery.FAILED);
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts()).singleElement().asString().contains("Not posted");
  }

  @Test
  void anUnexpectedException_failsTheReplyAfterAcknowledging() {
    when(reports.deliverSection(MATCH, "i1", "lanes")).thenThrow(new IllegalStateException("x"));
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getFailures()).singleElement().asString().contains("failed");
  }

  @Test
  void missingSection_isRefusedBeforeAcknowledgingListingTheChoices() {
    FakeCommandContext context = context(null);

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies())
        .singleElement()
        .asString()
        .contains("all, lanes, opendota");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void unknownSection_isRefusedBeforeAcknowledging() {
    FakeCommandContext context = context("lanez");

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies())
        .singleElement()
        .asString()
        .contains("Unknown section `lanez`")
        .contains("lanes");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(cooldown, never()).acquire(any());
  }

  @Test
  void aSectionHiddenByTheKillSwitch_isRefused() {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(false);
    properties.getSections().put("lanes", toggle);
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies()).singleElement().asString().contains("switched off");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(reports, never()).deliverSection(anyLong(), anyString(), anyString());
  }

  @Test
  void noStoredRun_isRefusedBeforeAcknowledging() {
    when(reports.hasStoredAnalysis(MATCH)).thenReturn(false);
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies())
        .singleElement()
        .asString()
        .contains("no stored replay analysis");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(cooldown, never()).acquire(any());
  }

  @Test
  void cooldownRunning_isRefusedBeforeAcknowledgingAndKeyedByThread() {
    when(cooldown.acquire("thread-1")).thenReturn(17L);
    FakeCommandContext context = context("lanes");

    handler.handle(context, MATCH, config);

    assertThat(context.getEphemeralReplies()).singleElement().asString().contains("17 s");
    assertThat(context.getAcknowledgeSummary()).isNull();
    verify(reports, never()).deliverSection(anyLong(), anyString(), anyString());
  }

  @Test
  void nothingPosted_releasesTheWaitButAPostKeepsIt() {
    for (ReportDelivery miss :
        List.of(
            ReportDelivery.SECTION_EMPTY, ReportDelivery.THREAD_NOT_FOUND, ReportDelivery.FAILED)) {
      when(reports.deliverSection(MATCH, "i1", "lanes")).thenReturn(miss);
      handler.handle(context("lanes"), MATCH, config);
    }
    verify(cooldown, org.mockito.Mockito.times(3)).release("thread-1");

    when(reports.deliverSection(MATCH, "i1", "lanes")).thenReturn(ReportDelivery.POSTED);
    handler.handle(context("lanes"), MATCH, config);
    verify(cooldown, org.mockito.Mockito.times(3)).release("thread-1");
  }

  @Test
  void anException_releasesTheWait() {
    when(reports.deliverSection(MATCH, "i1", "lanes")).thenThrow(new IllegalStateException("x"));

    handler.handle(context("lanes"), MATCH, config);

    verify(cooldown).release("thread-1");
  }

  @Test
  void allWithNothingSetToPostAutomatically_saysSoPlainlyAndReleases() {
    when(reports.redeliver(MATCH, "i1")).thenReturn(ReportDelivery.NO_SECTIONS);
    FakeCommandContext context = context("all");

    handler.handle(context, MATCH, config);

    assertThat(context.getPosts())
        .singleElement()
        .asString()
        .contains("Nothing is set to post automatically in this channel")
        .contains("name a section instead");
    verify(cooldown).release("thread-1");
  }
}
