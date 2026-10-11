package com.ako.dbuff.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

/** Schedules one global cron job, replacing any copy left in the job store from an earlier run. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronJobRegistrar {
  private final Scheduler scheduler;

  public void register(
      JobKey key, Class<? extends Job> jobClass, boolean enabled, String cron, String description) {
    try {
      if (scheduler.checkExists(key)) {
        scheduler.deleteJob(key);
      }
      if (!enabled) {
        log.info("{} is disabled", description);
        return;
      }
      JobDetail job = JobBuilder.newJob(jobClass).withIdentity(key).build();
      Trigger trigger =
          TriggerBuilder.newTrigger()
              .forJob(key)
              .withSchedule(
                  CronScheduleBuilder.cronSchedule(cron).withMisfireHandlingInstructionDoNothing())
              .build();
      scheduler.scheduleJob(job, trigger);
      log.info("Scheduled {} with cron '{}'", description, cron);
    } catch (SchedulerException e) {
      log.error("Failed to schedule {}: {}", description, e.getMessage(), e);
    }
  }
}
