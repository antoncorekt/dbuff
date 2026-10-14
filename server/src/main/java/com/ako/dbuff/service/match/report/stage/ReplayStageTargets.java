package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
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
    Optional<ReplayParseRunDomain> run =
        runRepo.findByMatchIdAndParserVersion(match.getId(), processing.getParserVersion());
    if (run.isPresent()) {
      return Optional.of(
          StageTarget.of(run.get().getStatus(), run.get().retryLeft())
              .orElseGet(() -> StageTarget.at(MatchReportStage.REPLAY_PENDING)));
    }
    if (forecast.isExpected(match)) {
      return Optional.of(StageTarget.at(MatchReportStage.REPLAY_PENDING));
    }
    return match.getDetailsMappedAt() != null
        ? Optional.of(StageTarget.at(MatchReportStage.PARSED))
        : Optional.empty();
  }
}
