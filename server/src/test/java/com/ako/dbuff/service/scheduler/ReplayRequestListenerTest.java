package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRequested;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReplayRequestListenerTest {

  private final Scheduler scheduler = mock(Scheduler.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayRequestListener listener = new ReplayRequestListener(scheduler, properties);

  @Test
  void onReplayRequested_triggersTheReplayJobWhenProcessingIsOn() throws SchedulerException {
    properties.setEnabled(true);

    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler).triggerJob(JobKey.jobKey("replay-processing", "maintenance-jobs"));
  }

  @Test
  void onReplayRequested_doesNothingWhileProcessingIsOff() throws SchedulerException {
    listener.onReplayRequested(new ReplayRequested(1L));

    verify(scheduler, never()).triggerJob(any());
  }

  @Test
  void onReplayRequested_swallowsASchedulerFailure() throws SchedulerException {
    properties.setEnabled(true);
    doThrow(new SchedulerException("down")).when(scheduler).triggerJob(any());

    assertThatCode(() -> listener.onReplayRequested(new ReplayRequested(1L)))
        .doesNotThrowAnyException();
  }
}
