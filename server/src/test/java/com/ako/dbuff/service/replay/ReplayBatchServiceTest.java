package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import com.ako.dbuff.service.replay.file.ReplayTempCleaner;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
  private final ReplayReportService reportService = mock(ReplayReportService.class);
  private final ReplayBatchService batch =
      new ReplayBatchService(queue, runService, tempCleaner, janitor, reportService, properties);

  @Test
  void runBatch_processesMatchesOneAfterAnotherAndCountsStoredOnes() {
    queueOf(4);
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
    queueOf(5);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_DOWNLOAD);

    batch.runBatch();

    verify(runService, times(3)).process(any());
  }

  @Test
  void runBatch_doesNotStopWhenDownloadFailuresAreInterruptedBySuccess() {
    queueOf(5);
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
    queueOf(3);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_TIMEOUT);

    batch.runBatch();

    verify(tempCleaner).sweep();
    verify(janitor).closeAbandoned();
    verify(runService, times(3)).process(any());
  }

  @Test
  void runBatch_stopsAtOnceOnAResourceFailure() {
    queueOf(5);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_RESOURCE);

    batch.runBatch();

    verify(runService, times(1)).process(any());
  }

  @Test
  void runBatch_deliversEachReportRightAfterItsRunAndNotForFailedRuns() {
    queueOf(3);
    List<String> events = new ArrayList<>();
    when(runService.process(any()))
        .thenAnswer(
            call -> {
              long id = ((MatchDomain) call.getArgument(0)).getId();
              events.add("run" + id);
              return id == 2 ? ReplayStatus.FAILED_DOWNLOAD : ReplayStatus.DONE;
            });
    doAnswer(
            call -> {
              events.add("report" + call.getArgument(0));
              return null;
            })
        .when(reportService)
        .deliver(anyLong());

    batch.runBatch();

    assertThat(events).containsExactly("run1", "report1", "run2", "run3", "report3");
  }

  @Test
  void runBatch_keepsGoingWhenAReportCannotBeDelivered() {
    queueOf(2);
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);
    doThrow(new IllegalStateException("discord down")).when(reportService).deliver(anyLong());

    assertThat(batch.runBatch()).isEqualTo(2);

    verify(runService, times(2)).process(any());
  }

  @Test
  void runBatch_asksForTheNextMatchOnlyAfterTheLastOneFinished() {
    MatchDomain first = match(1);
    MatchDomain arrived = match(9);
    when(queue.next(anyInt()))
        .thenReturn(Optional.of(new QueuedMatch(first, Lane.NEW)))
        .thenReturn(Optional.of(new QueuedMatch(arrived, Lane.FRESH)))
        .thenReturn(Optional.empty());
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch();

    InOrder order = inOrder(queue, runService);
    order.verify(queue).next(anyInt());
    order.verify(runService).process(first);
    order.verify(queue).next(anyInt());
    order.verify(runService).process(arrived);
  }

  @Test
  void runBatch_stopsAtBatchSize() {
    properties.setBatchSize(2);
    when(queue.next(anyInt())).thenAnswer(call -> Optional.of(new QueuedMatch(match(1), Lane.NEW)));
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch();

    verify(runService, times(2)).process(any());
  }

  @Test
  void runBatch_countsBacklogMatchesAgainstTheBacklogBatchSize() {
    properties.getBacklog().setBatchSize(2);
    when(queue.next(anyInt()))
        .thenAnswer(call -> Optional.of(new QueuedMatch(match(1), Lane.BACKLOG)));
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch();

    verify(queue).next(2);
    verify(queue).next(1);
  }

  private void queueOf(int count) {
    Iterator<QueuedMatch> iterator =
        LongStream.rangeClosed(1, count)
            .mapToObj(id -> new QueuedMatch(match(id), Lane.NEW))
            .iterator();
    when(queue.next(anyInt()))
        .thenAnswer(call -> iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty());
  }

  private static MatchDomain match(long id) {
    MatchDomain match = new MatchDomain();
    match.setId(id);
    return match;
  }
}
