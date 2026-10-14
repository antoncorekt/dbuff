package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.Optional;

/**
 * The stage a header is to show and whether its replay analysis is partial.
 *
 * @param stage the stage to show
 * @param partial the analysis is there but some of it failed for good
 */
public record StageTarget(MatchReportStage stage, boolean partial) {

  public static StageTarget at(MatchReportStage stage) {
    return new StageTarget(stage, false);
  }

  /**
   * The outcome of a run, empty while the run may still change the answer: it is running, or a
   * transient failure or partial result has a retry left. A transient failure with no retry left is
   * final and counts as unavailable.
   */
  public static Optional<StageTarget> of(ReplayStatus status, boolean retryLeft) {
    if (status == ReplayStatus.RUNNING || (status.isRetryable() && retryLeft)) {
      return Optional.empty();
    }
    return switch (status) {
      case DONE -> Optional.of(at(MatchReportStage.REPLAY_DONE));
      case PARTIAL -> Optional.of(new StageTarget(MatchReportStage.REPLAY_DONE, true));
      default -> Optional.of(at(MatchReportStage.REPLAY_UNAVAILABLE));
    };
  }
}
