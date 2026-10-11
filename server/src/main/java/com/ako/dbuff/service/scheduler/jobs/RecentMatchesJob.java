package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.LastMatchesProcessorService;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Never overlaps itself for one instance: a first-run import can outlast the 5-minute trigger, and
 * a second run would otherwise import the same matches again.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class RecentMatchesJob implements Job {

  @Autowired private LastMatchesProcessorService lastMatchesProcessorService;
  @Autowired private QuietHoursGuard quietHoursGuard;
  @Autowired private DbufInstanceConfigService instanceConfigService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
    InstanceSettings settings =
        instanceConfigService
            .getDomainById(instanceId)
            .map(DbufInstanceConfigDomain::effectiveSettings)
            .orElseGet(InstanceSettings::new);
    if (quietHoursGuard.isQuietTime(settings)) {
      log.info("Skipping recent matches for instance {} during quiet hours", instanceId);
      return;
    }
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
