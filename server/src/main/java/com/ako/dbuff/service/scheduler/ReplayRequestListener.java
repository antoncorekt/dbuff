package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRequested;
import com.ako.dbuff.service.scheduler.jobs.ReplayProcessingJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the replay job at once when a match gets its replay location, unless the job is already
 * running or waiting to run, or it is quiet hours (the cron picks the match up later).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRequestListener {
  private final Scheduler scheduler;
  private final ReplayProcessingProperties properties;
  private final QuietHoursGuard quietHours;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onReplayRequested(ReplayRequested event) {
    if (!properties.isEnabled() || quietHours.isServerQuietTime()) {
      return;
    }
    try {
      if (runningOrPending()) {
        return;
      }
      JobDataMap data = new JobDataMap();
      data.put(ReplayProcessingJob.NO_BACKLOG, true);
      scheduler.triggerJob(ReplayProcessingScheduler.JOB_KEY, data);
    } catch (SchedulerException | RuntimeException e) {
      log.warn("Could not trigger replay processing for match {}", event.matchId(), e);
    }
  }

  private boolean runningOrPending() throws SchedulerException {
    for (JobExecutionContext running : scheduler.getCurrentlyExecutingJobs()) {
      if (running.getJobDetail().getKey().equals(ReplayProcessingScheduler.JOB_KEY)) {
        return true;
      }
    }
    for (Trigger trigger : scheduler.getTriggersOfJob(ReplayProcessingScheduler.JOB_KEY)) {
      if (!(trigger instanceof CronTrigger)) {
        return true;
      }
    }
    return false;
  }
}
