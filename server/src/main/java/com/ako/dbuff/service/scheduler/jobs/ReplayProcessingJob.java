package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.service.replay.ReplayBatchService;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
public class ReplayProcessingJob implements Job {
  @Autowired private ReplayBatchService batchService;
  @Autowired private QuietHoursGuard quietHoursGuard;
  @Autowired private ReplayReportService reportService;

  @Override
  public void execute(JobExecutionContext context) {
    if (quietHoursGuard.isServerQuietTime()) {
      log.debug("Skipping replay processing during quiet hours");
      return;
    }
    try {
      batchService.runBatch();
    } catch (Exception e) {
      log.error("Replay processing failed: {}", e.getMessage(), e);
    }
    try {
      reportService.deliverPending();
    } catch (Exception e) {
      log.error("Replay report delivery failed: {}", e.getMessage(), e);
    }
  }
}
