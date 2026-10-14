package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayForecast;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The stage a match's header should show now, worked out from what is stored. */
@Component
@RequiredArgsConstructor
public class ReplayStageTargets {
  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties processing;
  private final ReplayForecast forecast;

  /** Empty when nothing is known beyond the first stage. */
  public Optional<StageTarget> forMatch(MatchDomain match) {
    Optional<StageTarget> outcome =
        runRepo
            .findByMatchIdAndParserVersion(match.getId(), processing.getParserVersion())
            .flatMap(run -> StageTarget.of(run.getStatus(), run.getNextAttemptAt() != null));
    if (outcome.isPresent()) {
      return outcome;
    }
    if (forecast.isExpected(match)) {
      return Optional.of(new StageTarget(MatchReportStage.REPLAY_PENDING, false));
    }
    return match.getDetailsMappedAt() != null
        ? Optional.of(new StageTarget(MatchReportStage.PARSED, false))
        : Optional.empty();
  }
}
