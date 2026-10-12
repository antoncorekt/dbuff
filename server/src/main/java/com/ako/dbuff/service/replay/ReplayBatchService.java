package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.QueuedMatch.Lane;
import com.ako.dbuff.service.replay.file.ReplayTempCleaner;
import com.ako.dbuff.service.replay.report.ReplayReportService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** One job run: up to a few matches, one after another, stopping when the host is struggling. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayBatchService {
  private static final int MAX_CONSECUTIVE_DOWNLOAD_FAILURES = 3;

  private final ReplayQueue queue;
  private final ReplayRunService runService;
  private final ReplayTempCleaner tempCleaner;
  private final ReplayRunJanitor janitor;
  private final ReplayReportService reportService;
  private final ReplayProcessingProperties properties;

  /**
   * Takes one match at a time from the queue, so a match that turns fresh meanwhile goes next, and
   * posts each match's report as soon as its run ends. A match is taken at most once per run.
   *
   * @param backlogAllowed whether this run may take backlog matches; a run triggered by a new
   *     replay must not
   * @return number of matches whose run ended DONE or PARTIAL
   */
  public int runBatch(boolean backlogAllowed) {
    tempCleaner.sweep();
    janitor.closeAbandoned();
    int backlogRoom = backlogAllowed ? properties.getBacklog().getBatchSize() : 0;
    Set<Long> attempted = new HashSet<>();
    int taken = 0;
    int stored = 0;
    int downloadFailures = 0;
    while (taken < properties.getBatchSize()) {
      Optional<QueuedMatch> next = queue.next(backlogRoom, attempted);
      if (next.isEmpty()) {
        break;
      }
      taken++;
      MatchDomain match = next.get().match();
      attempted.add(match.getId());
      if (next.get().lane() == Lane.BACKLOG) {
        backlogRoom--;
      }
      ReplayStatus status = runService.process(match);
      if (status == ReplayStatus.DONE || status == ReplayStatus.PARTIAL) {
        stored++;
        deliverReport(match.getId());
      }
      downloadFailures = status == ReplayStatus.FAILED_DOWNLOAD ? downloadFailures + 1 : 0;
      if (status == ReplayStatus.FAILED_RESOURCE
          || downloadFailures >= MAX_CONSECUTIVE_DOWNLOAD_FAILURES) {
        log.warn("Replay batch stopped after {} for match {}", status, match.getId());
        break;
      }
    }
    if (taken > 0) {
      log.info("Replay batch: {} of {} matches stored", stored, taken);
    }
    return stored;
  }

  private void deliverReport(long matchId) {
    try {
      reportService.deliverFinished(matchId);
    } catch (RuntimeException e) {
      log.error("Replay report delivery for match {} failed: {}", matchId, e.getMessage(), e);
    }
  }
}
