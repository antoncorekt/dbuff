package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.match.backfill.MatchDetailBackfillProperties;
import com.ako.dbuff.service.scheduler.jobs.MatchDetailBackfillJob;
import lombok.RequiredArgsConstructor;
import org.quartz.JobKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Schedules the one global backfill job, as opposed to the per-instance jobs. */
@Service
@RequiredArgsConstructor
public class MatchDetailBackfillScheduler {
  private static final JobKey JOB_KEY = JobKey.jobKey("match-detail-backfill", "maintenance-jobs");

  private final CronJobRegistrar registrar;
  private final MatchDetailBackfillProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    registrar.register(
        JOB_KEY,
        MatchDetailBackfillJob.class,
        properties.isEnabled(),
        properties.getCron(),
        "match detail backfill");
  }
}
