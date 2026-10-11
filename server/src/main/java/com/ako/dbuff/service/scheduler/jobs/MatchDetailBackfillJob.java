package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.service.match.backfill.MatchDetailBackfillService;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
public class MatchDetailBackfillJob implements Job {

  @Autowired private MatchDetailBackfillService backfillService;
  @Autowired private QuietHoursGuard quietHoursGuard;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    if (quietHoursGuard.isQuietTime()) {
      log.debug("Skipping match detail backfill during quiet hours");
      return;
    }
    try {
      backfillService.backfillBatch();
    } catch (Exception e) {
      log.error("Match detail backfill failed: {}", e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }
}
