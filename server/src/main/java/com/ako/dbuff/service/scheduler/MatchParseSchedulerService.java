package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.config.DotaApiConfig.DotaApiConfigurationProperties;
import com.ako.dbuff.service.scheduler.jobs.MatchParseCheckJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DateBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchParseSchedulerService {

  private static final String GROUP = "parse-wait-jobs";

  private final Scheduler scheduler;
  private final DotaApiConfigurationProperties config;

  public void scheduleParseWait(
      long matchId, String instanceId, Long discordThreadId, Long headerMessageId) {
    String name = "parse-wait-" + matchId;
    JobKey jobKey = JobKey.jobKey(name, GROUP);
    TriggerKey triggerKey = TriggerKey.triggerKey(name, GROUP);

    try {
      if (scheduler.checkExists(jobKey)) {
        log.debug("Parse wait job already exists for match {}, skipping", matchId);
        return;
      }

      var jobDataMap =
          JobBuilder.newJob(MatchParseCheckJob.class)
              .withIdentity(jobKey)
              .usingJobData("matchId", matchId)
              .usingJobData("instanceId", instanceId);

      if (discordThreadId != null) {
        jobDataMap.usingJobData("discordThreadId", discordThreadId);
      }
      if (headerMessageId != null) {
        jobDataMap.usingJobData("headerMessageId", headerMessageId);
      }

      JobDetail jobDetail = jobDataMap.storeDurably().build();

      int intervalSeconds = config.getParsePollIntervalSeconds();

      SimpleTrigger trigger =
          TriggerBuilder.newTrigger()
              .withIdentity(triggerKey)
              .forJob(jobKey)
              .startAt(DateBuilder.futureDate(intervalSeconds, DateBuilder.IntervalUnit.SECOND))
              .withSchedule(
                  SimpleScheduleBuilder.simpleSchedule()
                      .withIntervalInSeconds(intervalSeconds)
                      .repeatForever()
                      .withMisfireHandlingInstructionNextWithRemainingCount())
              .build();

      scheduler.scheduleJob(jobDetail, trigger);
      log.info("Scheduled parse wait for match {} (every {}s)", matchId, intervalSeconds);
    } catch (SchedulerException e) {
      log.error("Failed to schedule parse wait for match {}: {}", matchId, e.getMessage(), e);
    }
  }

  public void unscheduleParseWait(long matchId) {
    JobKey jobKey = JobKey.jobKey("parse-wait-" + matchId, GROUP);
    try {
      if (scheduler.checkExists(jobKey)) {
        scheduler.deleteJob(jobKey);
        log.debug("Unscheduled parse wait for match {}", matchId);
      }
    } catch (SchedulerException e) {
      log.error("Failed to unschedule parse wait for match {}: {}", matchId, e.getMessage(), e);
    }
  }
}
