package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayRunFinished;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayRunFinishedListenerTest {
  private final MatchReportStageService stages = mock(MatchReportStageService.class);
  private final ReplayRunFinishedListener listener = new ReplayRunFinishedListener(stages);
  private final ReplayRunFinished event = new ReplayRunFinished(7L, ReplayStatus.PARTIAL, false);

  @Test
  void passesTheRunOutcomeToTheStagesWhenTheSwitchIsOn() {
    when(stages.enabled()).thenReturn(true);

    listener.onRunFinished(event);

    verify(stages).replayFinished(7L, ReplayStatus.PARTIAL, false);
  }

  @Test
  void doesNothingWhenTheSwitchIsOff() {
    listener.onRunFinished(event);

    verify(stages, never()).replayFinished(anyLong(), any(), anyBoolean());
  }

  @Test
  void neverThrowsIntoThePublisher() {
    when(stages.enabled()).thenReturn(true);
    doThrow(new IllegalStateException("db down"))
        .when(stages)
        .replayFinished(anyLong(), any(), anyBoolean());

    assertThatCode(() -> listener.onRunFinished(event)).doesNotThrowAnyException();
  }
}
