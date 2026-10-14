package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The status of a match's replay run, shared by the status endpoint and the analysis document. */
@Service
@RequiredArgsConstructor
public class RunStatusReader {
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;

  public Optional<RunStatus> read(long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, properties.getParserVersion())
        .map(
            run ->
                new RunStatus(
                    run.getMatchId(),
                    run.getStatus(),
                    run.getAttempts(),
                    run.getError(),
                    run.getMapVersion()));
  }
}
