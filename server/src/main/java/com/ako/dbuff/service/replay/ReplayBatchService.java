package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.file.ReplayTempCleaner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** One job run: the next few matches, one after another, stopping when the host is struggling. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayBatchService {
  private static final int MAX_CONSECUTIVE_DOWNLOAD_FAILURES = 3;

  private final ReplayQueue queue;
  private final ReplayRunService runService;
  private final ReplayTempCleaner tempCleaner;
  private final ReplayRunJanitor janitor;
  private final ReplayProcessingProperties properties;

  /**
   * @return number of matches whose run ended DONE or PARTIAL
   */
  public int runBatch() {
    tempCleaner.sweep();
    janitor.closeAbandoned();
    List<MatchDomain> matches = queue.next(properties.getBatchSize());
    int stored = 0;
    int downloadFailures = 0;
    for (MatchDomain match : matches) {
      ReplayStatus status = runService.process(match);
      if (status == ReplayStatus.DONE || status == ReplayStatus.PARTIAL) {
        stored++;
      }
      downloadFailures = status == ReplayStatus.FAILED_DOWNLOAD ? downloadFailures + 1 : 0;
      if (status == ReplayStatus.FAILED_RESOURCE
          || downloadFailures >= MAX_CONSECUTIVE_DOWNLOAD_FAILURES) {
        log.warn("Replay batch stopped after {} for match {}", status, match.getId());
        break;
      }
    }
    if (!matches.isEmpty()) {
      log.info("Replay batch: {} of {} matches stored", stored, matches.size());
    }
    return stored;
  }
}
