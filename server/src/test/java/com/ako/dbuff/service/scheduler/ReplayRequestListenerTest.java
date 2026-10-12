package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRequested;
import com.ako.dbuff.service.scheduler.jobs.ReplayProcessingJob;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayRequestListenerTest {

  private static final JobKey KEY = JobKey.jobKey("replay-processing", "maintenance-jobs");

  private final Scheduler scheduler = mock(Scheduler.class);
  private final QuietHoursGuard quietHours = mock(QuietHoursGuard.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayRequestListener listener =
      new ReplayRequestListener(scheduler, properties, quietHours);

  @BeforeEach
  void setUp() throws SchedulerException {
    properties.setEnabled(true);
    when(scheduler.getCurrentlyExecutingJobs()).thenReturn(List.of());
    doReturn(List.of(mock(CronTrigger.class))).when(scheduler).getTriggersOfJob(KEY);
  }

  @Test
  void onReplayRequested_triggersTheJobWithoutBacklog() throws SchedulerException {
    listener.onReplayRequested(new ReplayRequested(1L));

    ArgumentCaptor<JobDataMap> data = ArgumentCaptor.forClass(JobDataMap.class);
    verify(scheduler).triggerJob(org.mockito.ArgumentMatchers.eq(KEY), data.capture());
    assertThat(data.getValue().containsKey(ReplayProcessingJob.NO_BACKLOG)).isTrue();
  }

  @Test
  void onReplayRequested_doesNothingWhileProcessingIsOff() throws SchedulerException {
    properties.setEnabled(false);

    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler, never()).triggerJob(any(), any(JobDataMap.class));
  }

  @Test
  void onReplayRequested_doesNothingInQuietHours() throws SchedulerException {
    when(quietHours.isServerQuietTime()).thenReturn(true);

    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler, never()).triggerJob(any(), any(JobDataMap.class));
  }

  @Test
  void onReplayRequested_doesNothingWhileTheJobIsRunning() throws SchedulerException {
    JobExecutionContext context = mock(JobExecutionContext.class);
    JobDetail detail = mock(JobDetail.class);
    when(detail.getKey()).thenReturn(KEY);
    when(context.getJobDetail()).thenReturn(detail);
    when(scheduler.getCurrentlyExecutingJobs()).thenReturn(List.of(context));

    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler, never()).triggerJob(any(), any(JobDataMap.class));
  }

  @Test
  void onReplayRequested_doesNothingWhileATriggerIsAlreadyPending() throws SchedulerException {
    doReturn(List.of(mock(CronTrigger.class), mock(SimpleTrigger.class)))
        .when(scheduler)
        .getTriggersOfJob(KEY);

    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler, never()).triggerJob(any(), any(JobDataMap.class));
  }

  @Test
  void onReplayRequested_swallowsASchedulerFailure() throws SchedulerException {
    doThrow(new SchedulerException("down"))
        .when(scheduler)
        .triggerJob(any(), any(JobDataMap.class));

    assertThatCode(() -> listener.onReplayRequested(new ReplayRequested(1L)))
        .doesNotThrowAnyException();
  }

  @Test
  void onReplayRequested_runsAfterCommitOrImmediatelyWithoutATransaction()
      throws NoSuchMethodException {
    TransactionalEventListener annotation =
        ReplayRequestListener.class
            .getMethod("onReplayRequested", ReplayRequested.class)
            .getAnnotation(TransactionalEventListener.class);

    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(annotation.fallbackExecution()).isTrue();
  }
}
