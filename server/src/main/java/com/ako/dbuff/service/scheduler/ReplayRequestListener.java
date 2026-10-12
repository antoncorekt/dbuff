package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Runs the replay job at once when a match gets its replay location. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRequestListener {
  private final Scheduler scheduler;
  private final ReplayProcessingProperties properties;

  @EventListener
  public void onReplayRequested(ReplayRequested event) {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      scheduler.triggerJob(ReplayProcessingScheduler.JOB_KEY);
    } catch (SchedulerException | RuntimeException e) {
      log.warn("Could not trigger replay processing for match {}", event.matchId(), e);
    }
  }
}
