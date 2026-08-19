package com.ako.dbuff.service.scheduler.jobs;

import com.ako.dbuff.config.DotaApiConfig.DotaApiConfigurationProperties;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.details.MatchParserHandler;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.DotaApiParseRequestService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.scheduler.MatchParseSchedulerService;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.time.Duration;
import java.time.LocalDateTime;
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
public class MatchParseCheckJob implements Job {

  @Autowired private DotaApiParseRequestService parseRequestService;
  @Autowired private MatchParserHandler matchParserHandler;
  @Autowired private MatchRepo matchRepo;
  @Autowired private MatchReportOrchestrator matchReportOrchestrator;
  @Autowired private DbufInstanceConfigService instanceConfigService;
  @Autowired private MatchParseSchedulerService matchParseSchedulerService;
  @Autowired private DotaApiConfigurationProperties config;
  @Autowired private QuietHoursGuard quietHoursGuard;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    long matchId = context.getJobDetail().getJobDataMap().getLong("matchId");
    String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
    long discordThreadId = context.getJobDetail().getJobDataMap().getLong("discordThreadId");
    long headerMessageId = context.getJobDetail().getJobDataMap().getLong("headerMessageId");

    if (quietHoursGuard.isQuietTime()) {
      log.debug("Skipping parse check for match {} during quiet hours", matchId);
      return;
    }

    log.debug("Checking parse status for match {}", matchId);

    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (match == null || match.getEndProcess() != null) {
      log.debug("Match {} already processed or not found, unscheduling", matchId);
      matchParseSchedulerService.unscheduleParseWait(matchId);
      return;
    }

    if (isTimedOut(match)) {
      log.warn(
          "Match {} parse wait timed out after {} hours", matchId, config.getParseMaxWaitHours());
      match.setError("Parse timeout exceeded");
      match.setEndProcess(LocalDateTime.now());
      matchRepo.save(match);
      matchParseSchedulerService.unscheduleParseWait(matchId);
      return;
    }

    if (!parseRequestService.isMatchParsed(matchId)) {
      log.debug("Match {} still not parsed, will retry on next trigger", matchId);
      return;
    }

    log.info("Match {} is now parsed, starting processing", matchId);
    try {
      MatchDomain processed = matchParserHandler.handle(matchId);
      if (processed != null && instanceId != null) {
        triggerFullReport(processed, instanceId, discordThreadId, headerMessageId);
      }
    } catch (Exception e) {
      log.error("Failed to process parsed match {}: {}", matchId, e.getMessage(), e);
    }
    matchParseSchedulerService.unscheduleParseWait(matchId);
  }

  private boolean isTimedOut(MatchDomain match) {
    if (match.getParseRequestedAt() == null) {
      return false;
    }
    Duration elapsed = Duration.between(match.getParseRequestedAt(), LocalDateTime.now());
    return elapsed.toHours() >= config.getParseMaxWaitHours();
  }

  private void triggerFullReport(
      MatchDomain match, String instanceId, long discordThreadId, long headerMessageId) {
    try {
      DbufInstanceConfigDomain instanceConfig =
          instanceConfigService.getDomainById(instanceId).orElse(null);
      if (instanceConfig == null || instanceConfig.getDiscordChannelId() == null) {
        log.debug("No discord channel for instance {}, skipping full report", instanceId);
        return;
      }
      if (discordThreadId > 0) {
        matchReportOrchestrator.processAndReportFull(
            match, instanceConfig, discordThreadId, headerMessageId);
      }
    } catch (Exception e) {
      log.error("Failed to send full report for match {}: {}", match.getId(), e.getMessage(), e);
    }
  }
}
