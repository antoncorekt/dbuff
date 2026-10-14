package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.ai.reasons.AiReasonsCommandHandler;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.match.DotaApiParseRequestService;
import com.ako.dbuff.service.match.MatchDeletionService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.replay.report.ReportCommandHandler;
import com.ako.dbuff.service.replay.report.ReportDelivery;
import com.ako.dbuff.service.scheduler.MatchParseSchedulerService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MatchCommandTest {

  private static final Long MATCH_ID = 8795480597L;
  private static final String THREAD = "Match " + MATCH_ID;

  private MatchRepo matchRepo;
  private DbufInstanceConfigRepository instanceConfigRepo;
  private MatchReportOrchestrator orchestrator;
  private MatchDeletionService matchDeletionService;
  private DotaApiParseRequestService parseRequestService;
  private MatchParseSchedulerService schedulerService;
  private MatchReportStageService stageService;
  private ReplayReportService replayReportService;
  private AiReasonsCommandHandler aiReasons;
  private ReportCommandHandler reportHandler;
  private MatchCommand command;

  @BeforeEach
  void setUp() {
    matchRepo = Mockito.mock(MatchRepo.class);
    instanceConfigRepo = Mockito.mock(DbufInstanceConfigRepository.class);
    orchestrator = Mockito.mock(MatchReportOrchestrator.class);
    matchDeletionService = Mockito.mock(MatchDeletionService.class);
    parseRequestService = Mockito.mock(DotaApiParseRequestService.class);
    schedulerService = Mockito.mock(MatchParseSchedulerService.class);
    stageService = Mockito.mock(MatchReportStageService.class);
    replayReportService = Mockito.mock(ReplayReportService.class);
    aiReasons = Mockito.mock(AiReasonsCommandHandler.class);
    reportHandler = Mockito.mock(ReportCommandHandler.class);

    command =
        new MatchCommand(
            matchRepo,
            instanceConfigRepo,
            orchestrator,
            matchDeletionService,
            parseRequestService,
            schedulerService,
            stageService,
            replayReportService,
            aiReasons,
            reportHandler);

    Mockito.when(instanceConfigRepo.findByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.of(config()));
    Mockito.when(matchRepo.findById(MATCH_ID)).thenReturn(Optional.of(match()));
    Mockito.when(matchRepo.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
  }

  private static DbufInstanceConfigDomain config() {
    DbufInstanceConfigDomain config = new DbufInstanceConfigDomain();
    config.setId("instance-1");
    config.setDiscordChannelId("channel-1");
    return config;
  }

  private static MatchDomain match() {
    return MatchDomain.builder().id(MATCH_ID).build();
  }

  private static FakeCommandContext inThread() {
    return FakeCommandContext.builder().threadName(THREAD).channelId("thread-99").build();
  }

  @Test
  void definitionExposesRerunAndRetry() {
    assertThat(command.getDefinition().getSubcommands())
        .extracting(subcommand -> subcommand.getName())
        .containsExactlyInAnyOrder("rerun", "retry", "ai", "report");
  }

  @Test
  void theAliasesAreTheSubcommandNames() {
    assertThat(command.getTextAliases())
        .containsExactlyInAnyOrder("rerun", "retry", "ai", "report");
    assertThat(command.resolveTextSubcommand("ai", null)).isEqualTo("ai");
    assertThat(command.resolveTextSubcommand("rerun", null)).isEqualTo("rerun");
    assertThat(command.resolveTextSubcommand("retry", null)).isEqualTo("retry");
  }

  @Test
  void ai_inAThreadHandsTheMatchToTheReasonsHandlerAndTouchesNothingElse() {
    FakeCommandContext context = inThread();

    command.execute("ai", context);

    Mockito.verify(aiReasons).handle(context, MATCH_ID, config());
    Mockito.verifyNoInteractions(orchestrator, replayReportService, matchDeletionService);
  }

  @Test
  void theTextFormOfAiTakesAnOptionalModel() {
    assertThat(command.parseTextArguments("ai", null, "")).isEmpty();
    assertThat(command.parseTextArguments("ai", "gpt-4o", "")).containsEntry("model", "gpt-4o");
    assertThat(command.parseTextArguments("rerun", null, "")).isEmpty();
    assertThat(command.getDefinition().getSubcommands())
        .filteredOn(s -> s.getName().equals("ai"))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getOptions()).hasSize(1);
              assertThat(s.getOptions().get(0).getName()).isEqualTo("model");
              assertThat(s.getOptions().get(0).isAutoComplete()).isTrue();
              assertThat(s.getOptions().get(0).isRequired()).isFalse();
            });
  }

  @Test
  void ai_outsideAThreadIsRefusedInOneLine() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("ai", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("/match ai");
    Mockito.verifyNoInteractions(aiReasons);
  }

  @Test
  void outsideAThread_repliesEphemerallyAndDoesNoWork() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("rerun", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("match thread");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(orchestrator);
  }

  @Test
  void threadNameWithNoParseableId_repliesEphemerally() {
    FakeCommandContext context = FakeCommandContext.builder().threadName("stats: Tigress").build();

    command.execute("rerun", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(orchestrator);
  }

  @Test
  void threadNameWithANonNumericId_repliesEphemerally() {
    FakeCommandContext context = FakeCommandContext.builder().threadName("Match ohno").build();

    command.execute("rerun", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    Mockito.verifyNoInteractions(orchestrator);
  }

  @Test
  void unregisteredChannel_repliesEphemerally() {
    Mockito.when(instanceConfigRepo.findByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    Mockito.verifyNoInteractions(orchestrator);
  }

  @Test
  void rerun_callsTheOrchestratorOnceWithThatMatch() {
    FakeCommandContext context = inThread();

    command.execute("rerun", context);

    assertThat(context.getAcknowledgeSummary()).contains(String.valueOf(MATCH_ID));
    Mockito.verify(orchestrator)
        .processAndReport(
            Mockito.argThat(
                matches -> matches.size() == 1 && matches.get(0).getId().equals(MATCH_ID)),
            Mockito.any());
  }

  @Test
  void rerun_withTheStagesOffNeverRefreshesOrRedelivers() {
    FakeCommandContext context = inThread();

    command.execute("rerun", context);

    Mockito.verify(stageService, Mockito.never()).refresh(Mockito.any(), Mockito.any());
    Mockito.verifyNoInteractions(replayReportService);
    assertThat(context.getPosts()).isEmpty();
  }

  @Test
  void rerun_withTheStagesOn_refreshesTheHeaderRedeliversAndSaysSo() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(stageService.refresh(Mockito.any(), Mockito.any())).thenReturn(true);
    Mockito.when(replayReportService.redeliver(MATCH_ID, "instance-1"))
        .thenReturn(ReportDelivery.POSTED);

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    Mockito.verify(stageService)
        .refresh(Mockito.argThat(m -> m.getId().equals(MATCH_ID)), Mockito.any());
    Mockito.verify(replayReportService).redeliver(MATCH_ID, "instance-1");
    Mockito.verifyNoInteractions(orchestrator);
    assertThat(context.getPosts()).containsExactly("Header refreshed. Replay report posted again.");
  }

  @Test
  void rerun_withTheStagesOn_saysWhyTheReportWasNotPosted() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(replayReportService.redeliver(MATCH_ID, "instance-1"))
        .thenReturn(ReportDelivery.NOT_STORED);

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getPosts())
        .containsExactly(
            "Header not changed (plain header, no stored thread or the edit failed). Replay report"
                + " not posted: no replay analysis is stored for this match.");
    assertThat(context.getFailures()).isEmpty();
  }

  @Test
  void rerun_aFailingRefreshNeverFailsTheCommand() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(stageService.refresh(Mockito.any(), Mockito.any()))
        .thenThrow(new IllegalStateException("db down"));

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getFailures()).isEmpty();
    assertThat(context.getPosts()).hasSize(1);
    assertThat(context.getPosts().get(0)).startsWith("Header not changed");
  }

  @Test
  void rerun_withTheStagesOn_neverLeavesTheUserWithoutALine() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(replayReportService.redeliver(MATCH_ID, "instance-1"))
        .thenThrow(new IllegalStateException("db down"));

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getPosts())
        .containsExactly(
            "Header not changed (plain header, no stored thread or the edit failed). Replay report"
                + " repost failed.");
  }

  @Test
  void rerun_aFailingRefreshStillReportsTheRepost() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(stageService.refresh(Mockito.any(), Mockito.any()))
        .thenThrow(new IllegalStateException("db down"));
    Mockito.when(replayReportService.redeliver(MATCH_ID, "instance-1"))
        .thenReturn(ReportDelivery.POSTED);

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getPosts())
        .containsExactly(
            "Header not changed (plain header, no stored thread or the edit failed). Replay report"
                + " posted again.");
  }

  @Test
  void retry_neverRefreshesTheHeader() {
    Mockito.when(stageService.enabled()).thenReturn(true);
    Mockito.when(orchestrator.processAndReportPartial(Mockito.any(), Mockito.any()))
        .thenReturn(new PartialReportResult(555L, 666L));

    command.execute("retry", inThread());

    Mockito.verify(stageService, Mockito.never()).refresh(Mockito.any(), Mockito.any());
    Mockito.verifyNoInteractions(replayReportService);
  }

  @Test
  void rerun_matchNotStored_pointsAtRetryInsteadOfFailingInAThread() {
    Mockito.when(matchRepo.findById(MATCH_ID)).thenReturn(Optional.empty());

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("/match retry");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(orchestrator);
  }

  @Test
  void rerun_orchestratorThrowing_reportsInTheThread() {
    Mockito.doThrow(new IllegalStateException("OpenAI down"))
        .when(orchestrator)
        .processAndReport(Mockito.anyList(), Mockito.any());

    FakeCommandContext context = inThread();
    command.execute("rerun", context);

    assertThat(context.getFailures()).hasSize(1);
    assertThat(context.getFailures().get(0)).contains("OpenAI down");
  }

  @Test
  void retry_deletesTheStoredMatchThenRequestsAParse() {
    Mockito.when(orchestrator.processAndReportPartial(Mockito.any(), Mockito.any()))
        .thenReturn(new PartialReportResult(555L, 666L));

    FakeCommandContext context = inThread();
    command.execute("retry", context);

    Mockito.verify(matchDeletionService).deleteMatch(MATCH_ID);
    Mockito.verify(parseRequestService).submitParseRequest(MATCH_ID);
    Mockito.verify(schedulerService).scheduleParseWait(MATCH_ID, "instance-1", 555L, 666L);
    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("Parse requested"));
  }

  @Test
  void retry_aHeaderWithoutAThread_schedulesAgainstTheCurrentThreadAndKeepsTheHeader() {
    Mockito.when(orchestrator.processAndReportPartial(Mockito.any(), Mockito.any()))
        .thenReturn(new PartialReportResult(0L, 666L));

    FakeCommandContext context =
        FakeCommandContext.builder().threadName(THREAD).channelId("12345").build();
    command.execute("retry", context);

    Mockito.verify(schedulerService).scheduleParseWait(MATCH_ID, "instance-1", 12345L, 666L);
  }

  @Test
  void retry_matchNotStored_skipsDeletionButStillReprocesses() {
    Mockito.when(matchRepo.findById(MATCH_ID)).thenReturn(Optional.empty());
    Mockito.when(orchestrator.processAndReportPartial(Mockito.any(), Mockito.any()))
        .thenReturn(new PartialReportResult(555L, 666L));

    FakeCommandContext context = inThread();
    command.execute("retry", context);

    Mockito.verify(matchDeletionService, Mockito.never()).deleteMatch(Mockito.anyLong());
    Mockito.verify(parseRequestService).submitParseRequest(MATCH_ID);
  }

  /**
   * Without a partial result there is no orchestrator-created thread, so the scheduler has to be
   * told to post back into the thread the command was run in — otherwise the completed analysis
   * goes nowhere.
   */
  @Test
  void retry_noPartialResult_schedulesAgainstTheCurrentThread() {
    Mockito.when(orchestrator.processAndReportPartial(Mockito.any(), Mockito.any()))
        .thenReturn(null);

    FakeCommandContext context =
        FakeCommandContext.builder().threadName(THREAD).channelId("12345").build();
    command.execute("retry", context);

    Mockito.verify(schedulerService).scheduleParseWait(MATCH_ID, "instance-1", 12345L, null);
  }

  @Test
  void retry_failing_reportsInTheThread() {
    Mockito.doThrow(new IllegalStateException("OpenDota refused"))
        .when(parseRequestService)
        .submitParseRequest(Mockito.anyLong());

    FakeCommandContext context = inThread();
    command.execute("retry", context);

    assertThat(context.getFailures()).hasSize(1);
    assertThat(context.getFailures().get(0)).contains("OpenDota refused");
  }

  @Test
  void bangRerunAndSlashMatchRerunTakeTheSamePath() {
    FakeCommandContext viaText = inThread();
    command.execute(command.resolveTextSubcommand("rerun", null), viaText);

    FakeCommandContext viaSlash = inThread();
    command.execute("rerun", viaSlash);

    assertThat(viaText.getAcknowledgeSummary()).isEqualTo(viaSlash.getAcknowledgeSummary());
    Mockito.verify(orchestrator, Mockito.times(2))
        .processAndReport(Mockito.anyList(), Mockito.any());
  }

  @Test
  void resultsGoIntoTheThreadItWasInvokedIn() {
    FakeCommandContext context = inThread();

    command.execute("rerun", context);

    assertThat(context.getAcknowledgeThreadName()).isEqualTo(THREAD);
  }

  @Test
  void unusedTextArgumentsAreIgnored() {
    assertThat(command.parseTextArguments("rerun", null, "some junk")).isEmpty();
  }

  @Test
  void listOfMatchesPassedToTheOrchestratorIsExactlyOne() {
    FakeCommandContext context = inThread();

    command.execute("rerun", context);

    Mockito.verify(orchestrator).processAndReport(Mockito.eq(List.of(match())), Mockito.any());
  }

  @Test
  void report_inAThreadHandsTheMatchToTheReportHandlerAndTouchesNothingElse() {
    FakeCommandContext context = inThread();

    command.execute("report", context);

    Mockito.verify(reportHandler).handle(context, MATCH_ID, config());
    Mockito.verifyNoInteractions(
        orchestrator, replayReportService, matchDeletionService, aiReasons);
  }

  @Test
  void report_outsideAThreadIsRefusedInOneLine() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("report", context);

    assertThat(context.getEphemeralReplies()).singleElement().asString().contains("/match report");
    Mockito.verifyNoInteractions(reportHandler);
  }

  @Test
  void theTextFormOfReportTakesTheSectionName() {
    assertThat(command.parseTextArguments("report", "lanes", "")).containsEntry("section", "lanes");
    assertThat(command.parseTextArguments("report", "all", "")).containsEntry("section", "all");
    assertThat(command.parseTextArguments("report", null, "")).isEmpty();
    assertThat(command.resolveTextSubcommand("report", "lanes")).isEqualTo("report");
    assertThat(command.getDefinition().getSubcommands())
        .filteredOn(s -> s.getName().equals("report"))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getOptions()).hasSize(1);
              assertThat(s.getOptions().get(0).getName()).isEqualTo("section");
              assertThat(s.getOptions().get(0).isAutoComplete()).isTrue();
              assertThat(s.getOptions().get(0).isRequired()).isTrue();
            });
  }
}
