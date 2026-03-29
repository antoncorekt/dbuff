package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.service.scheduler.jobs.HalfYearStatisticsJob;
import com.ako.dbuff.service.scheduler.jobs.MonthlyStatisticsJob;
import com.ako.dbuff.service.scheduler.jobs.RecentMatchesJob;
import com.ako.dbuff.service.scheduler.jobs.WeeklyStatisticsJob;
import java.util.List;
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
import org.quartz.TriggerKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceSchedulerService {

  private static final String GROUP = "instance-jobs";

  private static final String RECENT_MATCHES = "recent-matches";
  private static final String WEEKLY_STATISTICS = "weekly-statistics";
  private static final String MONTHLY_STATISTICS = "monthly-statistics";
  private static final String HALF_YEAR_STATISTICS = "half-year-statistics";

  private final Scheduler scheduler;
  private final DbufInstanceConfigRepository instanceConfigRepository;

  public void scheduleJobsForInstance(String instanceId) {
    try {
      scheduleJob(instanceId, RECENT_MATCHES, RecentMatchesJob.class, "0 0/5 * * * ?");
      scheduleJob(instanceId, WEEKLY_STATISTICS, WeeklyStatisticsJob.class, "0 0 0 ? * MON");
      scheduleJob(instanceId, MONTHLY_STATISTICS, MonthlyStatisticsJob.class, "0 0 0 1 * ?");
      scheduleJob(instanceId, HALF_YEAR_STATISTICS, HalfYearStatisticsJob.class, "0 0 0 1 1,7 ?");
      log.info("Scheduled all jobs for instance {}", instanceId);
    } catch (SchedulerException e) {
      log.error("Failed to schedule jobs for instance {}: {}", instanceId, e.getMessage(), e);
      throw new RuntimeException("Failed to schedule jobs for instance " + instanceId, e);
    }
  }

  public void unscheduleJobsForInstance(String instanceId) {
    try {
      deleteJob(instanceId, RECENT_MATCHES);
      deleteJob(instanceId, WEEKLY_STATISTICS);
      deleteJob(instanceId, MONTHLY_STATISTICS);
      deleteJob(instanceId, HALF_YEAR_STATISTICS);
      log.info("Unscheduled all jobs for instance {}", instanceId);
    } catch (SchedulerException e) {
      log.error("Failed to unschedule jobs for instance {}: {}", instanceId, e.getMessage(), e);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void rescheduleAllActiveInstances() {
    List<DbufInstanceConfigDomain> activeInstances = instanceConfigRepository.findByActiveTrue();
    log.info("Rescheduling jobs for {} active instances on startup", activeInstances.size());
    for (DbufInstanceConfigDomain instance : activeInstances) {
      try {
        scheduleJobsForInstance(instance.getId());
      } catch (Exception e) {
        log.error(
            "Failed to reschedule jobs for instance {}: {}", instance.getId(), e.getMessage(), e);
      }
    }
  }

  private void scheduleJob(
      String instanceId, String jobType, Class<? extends Job> jobClass, String cronExpression)
      throws SchedulerException {
    String name = jobType + "-" + instanceId;
    JobKey jobKey = JobKey.jobKey(name, GROUP);
    TriggerKey triggerKey = TriggerKey.triggerKey(name, GROUP);

    JobDetail jobDetail =
        JobBuilder.newJob(jobClass)
            .withIdentity(jobKey)
            .usingJobData("instanceId", instanceId)
            .storeDurably()
            .build();

    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .forJob(jobKey)
            .withSchedule(
                CronScheduleBuilder.cronSchedule(cronExpression)
                    .withMisfireHandlingInstructionDoNothing())
            .build();

    if (scheduler.checkExists(jobKey)) {
      scheduler.deleteJob(jobKey);
    }
    scheduler.scheduleJob(jobDetail, trigger);
    log.debug("Scheduled job {} with cron '{}'", name, cronExpression);
  }

  private void deleteJob(String instanceId, String jobType) throws SchedulerException {
    JobKey jobKey = JobKey.jobKey(jobType + "-" + instanceId, GROUP);
    if (scheduler.checkExists(jobKey)) {
      scheduler.deleteJob(jobKey);
    }
  }
}
