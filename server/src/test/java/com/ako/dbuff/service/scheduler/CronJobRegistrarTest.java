package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.scheduler.jobs.ReplayProcessingJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CronJobRegistrarTest {

  private static final JobKey KEY = JobKey.jobKey("job", "group");

  private final Scheduler scheduler = mock(Scheduler.class);
  private final CronJobRegistrar registrar = new CronJobRegistrar(scheduler);

  @Test
  void register_replacesAnOldJobAndSchedulesTheCronTrigger() throws SchedulerException {
    when(scheduler.checkExists(KEY)).thenReturn(true);

    registrar.register(KEY, ReplayProcessingJob.class, true, "0 3/10 * * * ?", "test job");

    verify(scheduler).deleteJob(KEY);
    ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
    ArgumentCaptor<JobDetail> job = ArgumentCaptor.forClass(JobDetail.class);
    verify(scheduler).scheduleJob(job.capture(), trigger.capture());
    assertThat(job.getValue().getJobClass()).isEqualTo(ReplayProcessingJob.class);
    assertThat(((CronTrigger) trigger.getValue()).getCronExpression()).isEqualTo("0 3/10 * * * ?");
  }

  @Test
  void register_removesButDoesNotScheduleADisabledJob() throws SchedulerException {
    when(scheduler.checkExists(KEY)).thenReturn(true);

    registrar.register(KEY, ReplayProcessingJob.class, false, "0 3/10 * * * ?", "test job");

    verify(scheduler).deleteJob(KEY);
    verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
  }
}
