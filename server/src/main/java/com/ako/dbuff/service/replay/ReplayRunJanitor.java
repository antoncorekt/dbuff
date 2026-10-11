package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Closes runs that were abandoned on their last attempt, which no retry would pick up. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRunJanitor {
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;

  public void closeAbandoned() {
    LocalDateTime now = LocalDateTime.now();
    List<ReplayParseRunDomain> abandoned =
        runRepo.findAbandonedWithoutAttemptsLeft(
            properties.getParserVersion(), now, properties.getMaxAttempts());
    for (ReplayParseRunDomain run : abandoned) {
      run.setStatus(ReplayStatus.FAILED_RESOURCE);
      run.setFinishedAt(now);
      run.setLeaseUntil(null);
      run.setNextAttemptAt(null);
      run.setError("Abandoned during its last attempt");
      runRepo.save(run);
      log.warn(
          "Replay run for match {} abandoned on attempt {}", run.getMatchId(), run.getAttempts());
    }
  }
}
