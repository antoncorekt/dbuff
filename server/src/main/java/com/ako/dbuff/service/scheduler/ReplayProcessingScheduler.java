package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.scheduler.jobs.ReplayProcessingJob;
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

/** Schedules the one global replay job. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayProcessingScheduler {
  private static final JobKey JOB_KEY = JobKey.jobKey("replay-processing", "maintenance-jobs");

  private final Scheduler scheduler;
  private final ReplayProcessingProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    try {
      if (scheduler.checkExists(JOB_KEY)) {
        scheduler.deleteJob(JOB_KEY);
      }
      if (!properties.isEnabled()) {
        log.info("Replay processing is disabled");
        return;
      }
      JobDetail job = JobBuilder.newJob(ReplayProcessingJob.class).withIdentity(JOB_KEY).build();
      Trigger trigger =
          TriggerBuilder.newTrigger()
              .forJob(JOB_KEY)
              .withSchedule(
                  CronScheduleBuilder.cronSchedule(properties.getCron())
                      .withMisfireHandlingInstructionDoNothing())
              .build();
      scheduler.scheduleJob(job, trigger);
      log.info("Scheduled replay processing with cron '{}'", properties.getCron());
    } catch (SchedulerException e) {
      log.error("Failed to schedule replay processing: {}", e.getMessage(), e);
    }
  }
}
