package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import com.ako.dbuff.service.replay.file.ReplayTempCleaner;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReplayBatchServiceTest {

  private final ReplayQueue queue = mock(ReplayQueue.class);
  private final ReplayRunService runService = mock(ReplayRunService.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayTempCleaner tempCleaner = mock(ReplayTempCleaner.class);
  private final ReplayRunJanitor janitor = mock(ReplayRunJanitor.class);
  private final ReplayReportService reportService = mock(ReplayReportService.class);
  private final MatchReportStageService stageService = mock(MatchReportStageService.class);
  private final ReplayParseRunRepo runRepo = mock(ReplayParseRunRepo.class);
  private final ReplayBatchService batch =
      new ReplayBatchService(
          queue,
          runService,
          tempCleaner,
          janitor,
          reportService,
          properties,
          stageService,
          runRepo);

  @Test
  void runBatch_processesMatchesOneAfterAnotherAndCountsStoredOnes() {
    queueOf(4);
    when(runService.process(any()))
        .thenReturn(
            ReplayStatus.DONE,
            ReplayStatus.EXPIRED,
            ReplayStatus.PARTIAL,
            ReplayStatus.SKIPPED_NOT_SUPPORTED);

    assertThat(batch.runBatch(true)).isEqualTo(2);

    verify(runService, times(4)).process(any());
  }

  @Test
  void runBatch_stopsAfterThreeDownloadFailuresInARow() {
    queueOf(5);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_DOWNLOAD);

    batch.runBatch(true);

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

    batch.runBatch(true);

    verify(runService, times(5)).process(any());
  }

  @Test
  void runBatch_sweepsLeftoverFilesFirst_andKeepsGoingAfterATimeout() {
    queueOf(3);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_TIMEOUT);

    batch.runBatch(true);

    verify(tempCleaner).sweep();
    verify(janitor).closeAbandoned();
    verify(runService, times(3)).process(any());
  }

  @Test
  void runBatch_stopsAtOnceOnAResourceFailure() {
    queueOf(5);
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_RESOURCE);

    batch.runBatch(true);

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
        .deliverFinished(anyLong());

    batch.runBatch(true);

    assertThat(events).containsExactly("run1", "report1", "run2", "run3", "report3");
  }

  @Test
  void runBatch_tellsTheStagesTheOutcomeWithTheRetryThatIsLeft() {
    queueOf(2);
    when(stageService.enabled()).thenReturn(true);
    when(runService.process(any())).thenReturn(ReplayStatus.PARTIAL, ReplayStatus.FAILED_DOWNLOAD);
    when(runRepo.findByMatchIdAndParserVersion(eq(1L), any()))
        .thenReturn(Optional.of(ReplayParseRunDomain.builder().build()));
    when(runRepo.findByMatchIdAndParserVersion(eq(2L), any()))
        .thenReturn(
            Optional.of(ReplayParseRunDomain.builder().nextAttemptAt(LocalDateTime.now()).build()));

    batch.runBatch(true);

    verify(stageService).replayFinished(1L, ReplayStatus.PARTIAL, false);
    verify(stageService).replayFinished(2L, ReplayStatus.FAILED_DOWNLOAD, true);
  }

  @Test
  void runBatch_withTheStagesOffNeverTouchesThemOrTheRunRepo() {
    queueOf(2);
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch(true);

    verify(stageService, never()).replayFinished(anyLong(), any(), anyBoolean());
    verifyNoInteractions(runRepo);
  }

  @Test
  void runBatch_keepsGoingWhenTheStageTitleCannotBeSet() {
    queueOf(3);
    when(stageService.enabled()).thenReturn(true);
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);
    doThrow(new IllegalStateException("db down"))
        .when(stageService)
        .replayFinished(anyLong(), any(), anyBoolean());

    assertThat(batch.runBatch(true)).isEqualTo(3);

    verify(reportService, times(3)).deliverFinished(anyLong());
  }

  @Test
  void runBatch_keepsGoingWhenAReportCannotBeDelivered() {
    queueOf(2);
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);
    doThrow(new IllegalStateException("discord down"))
        .when(reportService)
        .deliverFinished(anyLong());

    assertThat(batch.runBatch(true)).isEqualTo(2);

    verify(runService, times(2)).process(any());
  }

  @Test
  void runBatch_asksForTheNextMatchOnlyAfterTheLastOneFinished() {
    MatchDomain first = match(1);
    MatchDomain arrived = match(9);
    when(queue.next(anyInt(), any()))
        .thenReturn(Optional.of(new QueuedMatch(first, Lane.NEW)))
        .thenReturn(Optional.of(new QueuedMatch(arrived, Lane.FRESH)))
        .thenReturn(Optional.empty());
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch(true);

    InOrder order = inOrder(queue, runService);
    order.verify(queue).next(anyInt(), any());
    order.verify(runService).process(first);
    order.verify(queue).next(anyInt(), any());
    order.verify(runService).process(arrived);
  }

  @Test
  void runBatch_stopsAtBatchSize() {
    properties.setBatchSize(2);
    when(queue.next(anyInt(), any()))
        .thenAnswer(call -> Optional.of(new QueuedMatch(match(1), Lane.NEW)));
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch(true);

    verify(runService, times(2)).process(any());
  }

  @Test
  void runBatch_countsBacklogMatchesAgainstTheBacklogBatchSize() {
    properties.getBacklog().setBatchSize(2);
    when(queue.next(anyInt(), any()))
        .thenAnswer(call -> Optional.of(new QueuedMatch(match(1), Lane.BACKLOG)));
    when(runService.process(any())).thenReturn(ReplayStatus.DONE);

    batch.runBatch(true);

    verify(queue).next(eq(2), any());
    verify(queue).next(eq(1), any());
  }

  @Test
  void runBatch_neverOffersABacklogMatchWhenTheRunIsATriggeredOne() {
    when(queue.next(anyInt(), any())).thenReturn(Optional.empty());

    batch.runBatch(false);

    verify(queue).next(eq(0), any());
  }

  @Test
  void runBatch_excludesMatchesItAlreadyTookFromLaterQueueCalls() {
    List<Set<Long>> seen = new ArrayList<>();
    when(queue.next(anyInt(), any()))
        .thenAnswer(
            call -> {
              Set<Long> attempted = new HashSet<>(call.<Set<Long>>getArgument(1));
              seen.add(attempted);
              return attempted.size() < 2
                  ? Optional.of(new QueuedMatch(match(attempted.size() + 1), Lane.NEW))
                  : Optional.empty();
            });
    when(runService.process(any())).thenReturn(ReplayStatus.FAILED_TIMEOUT);

    batch.runBatch(true);

    assertThat(seen).containsExactly(Set.of(), Set.of(1L), Set.of(1L, 2L));
  }

  private void queueOf(int count) {
    Iterator<QueuedMatch> iterator =
        LongStream.rangeClosed(1, count)
            .mapToObj(id -> new QueuedMatch(match(id), Lane.NEW))
            .iterator();
    when(queue.next(anyInt(), any()))
        .thenAnswer(call -> iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty());
  }

  private static MatchDomain match(long id) {
    MatchDomain match = new MatchDomain();
    match.setId(id);
    return match;
  }
}
