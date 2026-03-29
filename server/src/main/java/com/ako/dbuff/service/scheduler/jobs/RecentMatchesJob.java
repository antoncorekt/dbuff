package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.service.match.LastMatchesProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RecentMatchesJob implements Job {

  @Autowired private LastMatchesProcessorService lastMatchesProcessorService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
    log.info("Processing last matches for instance {}", instanceId);
    try {
      lastMatchesProcessorService.processLastMatchesForInstance(instanceId);
    } catch (Exception e) {
      log.error(
          "Failed to process last matches for instance {}: {}", instanceId, e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }
}
