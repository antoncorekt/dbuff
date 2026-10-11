package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.scheduler.jobs.ReplayProcessingJob;
import lombok.RequiredArgsConstructor;
import org.quartz.JobKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Schedules the one global replay job. */
@Service
@RequiredArgsConstructor
public class ReplayProcessingScheduler {
  private static final JobKey JOB_KEY = JobKey.jobKey("replay-processing", "maintenance-jobs");

  private final CronJobRegistrar registrar;
  private final ReplayProcessingProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    registrar.register(
        JOB_KEY,
        ReplayProcessingJob.class,
        properties.isEnabled(),
        properties.getCron(),
        "replay processing");
  }
}
