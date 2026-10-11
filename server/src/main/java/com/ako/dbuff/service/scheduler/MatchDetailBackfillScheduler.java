package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.match.backfill.MatchDetailBackfillProperties;
import com.ako.dbuff.service.scheduler.jobs.MatchDetailBackfillJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Schedules the one global backfill job, as opposed to the per-instance jobs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchDetailBackfillScheduler {

  private static final JobKey JOB_KEY = JobKey.jobKey("match-detail-backfill", "maintenance-jobs");

  private final Scheduler scheduler;
  private final MatchDetailBackfillProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    try {
      if (scheduler.checkExists(JOB_KEY)) {
        scheduler.deleteJob(JOB_KEY);
      }
      if (!properties.isEnabled()) {
        log.info("Match detail backfill is disabled");
        return;
      }
      JobDetail job =
          JobBuilder.newJob(MatchDetailBackfillJob.class).withIdentity(JOB_KEY).build();
      Trigger trigger =
          TriggerBuilder.newTrigger()
              .forJob(JOB_KEY)
              .withSchedule(
                  CronScheduleBuilder.cronSchedule(properties.getCron())
                      .withMisfireHandlingInstructionDoNothing())
              .build();
      scheduler.scheduleJob(job, trigger);
      log.info("Scheduled match detail backfill with cron '{}'", properties.getCron());
    } catch (SchedulerException e) {
      log.error("Failed to schedule match detail backfill: {}", e.getMessage(), e);
    }
  }
}
