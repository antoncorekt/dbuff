package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.file.ReplayTempCleaner;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayBatchServiceTest {

  private final ReplayQueue queue = mock(ReplayQueue.class);
  private final ReplayRunService runService = mock(ReplayRunService.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayTempCleaner tempCleaner = mock(ReplayTempCleaner.class);
  private final ReplayRunJanitor janitor = mock(ReplayRunJanitor.class);
  private final ReplayBatchService batch =
      new ReplayBatchService(queue, runService, tempCleaner, janitor, properties);

  @Test
  void runBatch_processesMatchesOneAfterAnotherAndCountsStoredOnes() {
    when(queue.next(properties.getBatchSize())).thenReturn(matches(4));
    when(runService.process(any()))
        .thenReturn(
            ReplayStatus.DONE,
            ReplayStatus.EXPIRED,
            ReplayStatus.PARTIAL,
            ReplayStatus.SKIPPED_NOT_SUPPORTED);

    assertThat(batch.runBatch()).isEqualTo(2);

    verify(runService, times(4)).process(any());
  }

  @Test
  void runBatch_stopsAfterThreeDownloadFailuresInARow() {
    when(queue.next(properties.getBatchSize())).thenReturn(matches(5));
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_DOWNLOAD);

    batch.runBatch();

    verify(runService, times(3)).process(any());
  }

  @Test
  void runBatch_doesNotStopWhenDownloadFailuresAreInterruptedBySuccess() {
    when(queue.next(properties.getBatchSize())).thenReturn(matches(5));
    when(runService.process(any()))
        .thenReturn(
            ReplayStatus.FAILED_DOWNLOAD,
            ReplayStatus.FAILED_DOWNLOAD,
            ReplayStatus.DONE,
            ReplayStatus.FAILED_DOWNLOAD,
            ReplayStatus.FAILED_DOWNLOAD);

    batch.runBatch();

    verify(runService, times(5)).process(any());
  }

  @Test
  void runBatch_sweepsLeftoverFilesFirst_andKeepsGoingAfterATimeout() {
    when(queue.next(properties.getBatchSize())).thenReturn(matches(3));
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_TIMEOUT);

    batch.runBatch();

    verify(tempCleaner).sweep();
    verify(janitor).closeAbandoned();
    verify(runService, times(3)).process(any());
  }

  @Test
  void runBatch_stopsAtOnceOnAResourceFailure() {
    when(queue.next(properties.getBatchSize())).thenReturn(matches(5));
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_RESOURCE);

    batch.runBatch();

    verify(runService, times(1)).process(any());
  }

  private static List<MatchDomain> matches(int count) {
    return LongStream.rangeClosed(1, count)
        .mapToObj(
            id -> {
              MatchDomain match = new MatchDomain();
              match.setId(id);
              return match;
            })
        .toList();
  }
}
