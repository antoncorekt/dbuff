package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StageTargetTest {

  @ParameterizedTest
  @EnumSource(ReplayStatus.class)
  void everyStatusMapsAsTheRulesSay_withNoRetryLeft(ReplayStatus status) {
    Optional<StageTarget> target = StageTarget.of(status, false);

    switch (status) {
      case RUNNING -> assertThat(target).isEmpty();
      case DONE ->
          assertThat(target).contains(new StageTarget(MatchReportStage.REPLAY_DONE, false));
      case PARTIAL ->
          assertThat(target).contains(new StageTarget(MatchReportStage.REPLAY_DONE, true));
      default ->
          assertThat(target).contains(new StageTarget(MatchReportStage.REPLAY_UNAVAILABLE, false));
    }
  }

  @ParameterizedTest
  @EnumSource(ReplayStatus.class)
  void withARetryLeftOnlyDoneAndPermanentStatusesAreFinal(ReplayStatus status) {
    boolean isFinal = !status.isRetryable();

    assertThat(StageTarget.of(status, true).isPresent()).isEqualTo(isFinal);
  }
}
