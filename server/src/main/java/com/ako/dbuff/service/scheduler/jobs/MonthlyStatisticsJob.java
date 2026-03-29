package com.ako.dbuff.service.scheduler.jobs;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MonthlyStatisticsJob implements Job {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
    log.info("Monthly statistics job triggered for instance {} (not yet implemented)", instanceId);
  }
}
