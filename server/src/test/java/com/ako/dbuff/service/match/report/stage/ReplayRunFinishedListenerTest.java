package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayRunFinished;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayRunFinishedListenerTest {
  private final MatchReportStageService stages = mock(MatchReportStageService.class);
  private final ReplayRunFinishedListener listener = new ReplayRunFinishedListener(stages);
  private final ReplayRunFinished event = new ReplayRunFinished(7L, ReplayStatus.PARTIAL, false);

  @Test
  void passesTheRunOutcomeToTheStagesOnAnotherThreadWhenTheSwitchIsOn() throws Exception {
    when(stages.enabled()).thenReturn(true);
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Thread> worker = new AtomicReference<>();
    doAnswer(
            call -> {
              worker.set(Thread.currentThread());
              done.countDown();
              return null;
            })
        .when(stages)
        .replayFinished(7L, ReplayStatus.PARTIAL, false);

    listener.onRunFinished(event);

    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(worker.get()).isNotSameAs(Thread.currentThread());
    assertThat(worker.get().isVirtual()).isTrue();
  }

  @Test
  void doesNothingWhenTheSwitchIsOff() {
    listener.onRunFinished(event);

    verify(stages, never()).replayFinished(anyLong(), any(), anyBoolean());
  }

  @Test
  void neverThrowsIntoThePublisher() throws Exception {
    when(stages.enabled()).thenReturn(true);
    CountDownLatch called = new CountDownLatch(1);
    doAnswer(
            call -> {
              called.countDown();
              throw new IllegalStateException("db down");
            })
        .when(stages)
        .replayFinished(anyLong(), any(), anyBoolean());

    assertThatCode(() -> listener.onRunFinished(event)).doesNotThrowAnyException();
    assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
  }
}
