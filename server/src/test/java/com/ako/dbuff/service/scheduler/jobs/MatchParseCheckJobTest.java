package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.config.DotaApiConfig.DotaApiConfigurationProperties;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.details.MatchParserHandler;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.DotaApiParseRequestService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.scheduler.MatchParseSchedulerService;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchParseCheckJobTest {
  private static final long MATCH = 77L;
  private static final String INSTANCE = "instance-1";

  private final MatchParseCheckJob job = new MatchParseCheckJob();
  private final DotaApiParseRequestService parseRequests = mock(DotaApiParseRequestService.class);
  private final MatchParserHandler parserHandler = mock(MatchParserHandler.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final MatchReportOrchestrator orchestrator = mock(MatchReportOrchestrator.class);
  private final DbufInstanceConfigService instances = mock(DbufInstanceConfigService.class);
  private final MatchParseSchedulerService scheduler = mock(MatchParseSchedulerService.class);
  private final QuietHoursGuard quietHours = mock(QuietHoursGuard.class);
  private final MatchReportStageService stages = mock(MatchReportStageService.class);
  private final DotaApiConfigurationProperties config = new DotaApiConfigurationProperties();
  private final JobExecutionContext context = mock(JobExecutionContext.class);
  private final DbufInstanceConfigDomain instance =
      DbufInstanceConfigDomain.builder().id(INSTANCE).discordChannelId("c").build();
  private final MatchDomain match = MatchDomain.builder().id(MATCH).build();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(job, "parseRequestService", parseRequests);
    ReflectionTestUtils.setField(job, "matchParserHandler", parserHandler);
    ReflectionTestUtils.setField(job, "matchRepo", matchRepo);
    ReflectionTestUtils.setField(job, "matchReportOrchestrator", orchestrator);
    ReflectionTestUtils.setField(job, "instanceConfigService", instances);
    ReflectionTestUtils.setField(job, "matchParseSchedulerService", scheduler);
    ReflectionTestUtils.setField(job, "config", config);
    ReflectionTestUtils.setField(job, "quietHoursGuard", quietHours);
    ReflectionTestUtils.setField(job, "stageService", stages);
    JobDataMap data = new JobDataMap();
    data.put("matchId", MATCH);
    data.put("instanceId", INSTANCE);
    data.put("discordThreadId", 600L);
    data.put("headerMessageId", 500L);
    JobDetail detail = mock(JobDetail.class);
    when(detail.getJobDataMap()).thenReturn(data);
    when(context.getJobDetail()).thenReturn(detail);
    when(instances.getDomainById(INSTANCE)).thenReturn(Optional.of(instance));
    when(matchRepo.findById(MATCH)).thenReturn(Optional.of(match));
    when(quietHours.isQuietTime(any(InstanceSettings.class))).thenReturn(false);
    when(stages.enabled()).thenReturn(true);
  }

  private void waitedTooLong() {
    match.setParseRequestedAt(LocalDateTime.now().minusHours(config.getParseMaxWaitHours() + 1));
  }

  @Test
  void aTimedOutMatchGetsItsTitleEvenInQuietHours() throws Exception {
    waitedTooLong();
    when(quietHours.isQuietTime(any(InstanceSettings.class))).thenReturn(true);

    job.execute(context);

    verify(stages).parseTimedOut(match, INSTANCE);
    verify(matchRepo).save(match);
    assertThat(match.getError()).isEqualTo("Parse timeout exceeded");
    assertThat(match.getEndProcess()).isNotNull();
    verify(scheduler).unscheduleParseWait(MATCH);
  }

  @Test
  void aTimedOutMatchOutsideQuietHoursBehavesAsBefore() throws Exception {
    waitedTooLong();

    job.execute(context);

    verify(stages).parseTimedOut(match, INSTANCE);
    verify(parseRequests, never()).isMatchParsed(Mockito.anyLong());
    verify(scheduler).unscheduleParseWait(MATCH);
  }

  @Test
  void quietHoursStillSkipTheParseCheckOfAMatchThatHasNotTimedOut() throws Exception {
    match.setParseRequestedAt(LocalDateTime.now().minusHours(1));
    when(quietHours.isQuietTime(any(InstanceSettings.class))).thenReturn(true);

    job.execute(context);

    verify(parseRequests, never()).isMatchParsed(Mockito.anyLong());
    verify(stages, never()).parseTimedOut(any(), any());
    verify(scheduler, never()).unscheduleParseWait(Mockito.anyLong());
  }

  @Test
  void aProcessedMatchIsUnscheduledWithoutATitleChange() throws Exception {
    match.setEndProcess(LocalDateTime.now());
    waitedTooLong();

    job.execute(context);

    verify(stages, never()).parseTimedOut(any(), any());
    verify(scheduler).unscheduleParseWait(MATCH);
  }

  @Test
  void aParsedMatchIsRetitledBeforeTheOldFullReportRunsAndTheReportStillRuns() throws Exception {
    match.setParseRequestedAt(LocalDateTime.now().minusHours(1));
    when(parseRequests.isMatchParsed(MATCH)).thenReturn(true);
    MatchDomain processed = MatchDomain.builder().id(MATCH).build();
    when(parserHandler.handle(MATCH)).thenReturn(processed);

    job.execute(context);

    InOrder order = Mockito.inOrder(stages, orchestrator);
    order.verify(stages).parsed(processed, instance);
    order
        .verify(orchestrator)
        .processAndReportFull(eq(processed), eq(instance), eq(600L), eq(500L));
    verify(scheduler).unscheduleParseWait(MATCH);
  }

  @Test
  void aRetitleThatFailsDoesNotStopTheFullReport() throws Exception {
    match.setParseRequestedAt(LocalDateTime.now().minusHours(1));
    when(parseRequests.isMatchParsed(MATCH)).thenReturn(true);
    MatchDomain processed = MatchDomain.builder().id(MATCH).build();
    when(parserHandler.handle(MATCH)).thenReturn(processed);
    Mockito.doThrow(new IllegalStateException("boom")).when(stages).parsed(any(), any());

    job.execute(context);

    verify(orchestrator).processAndReportFull(eq(processed), eq(instance), eq(600L), eq(500L));
  }

  @Test
  void switchedOff_quietHoursComeFirstAsTheyAlwaysDid() throws Exception {
    when(stages.enabled()).thenReturn(false);
    waitedTooLong();
    when(quietHours.isQuietTime(any(InstanceSettings.class))).thenReturn(true);

    job.execute(context);

    verify(matchRepo, never()).findById(Mockito.anyLong());
    verify(stages, never()).parseTimedOut(any(), any());
    verify(scheduler, never()).unscheduleParseWait(Mockito.anyLong());
  }

  @Test
  void switchedOff_aTimedOutMatchOutsideQuietHoursStillTimesOut() throws Exception {
    when(stages.enabled()).thenReturn(false);
    waitedTooLong();

    job.execute(context);

    assertThat(match.getError()).isEqualTo("Parse timeout exceeded");
    verify(stages).parseTimedOut(match, INSTANCE);
    verify(scheduler).unscheduleParseWait(MATCH);
  }
}
