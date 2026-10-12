package com.ako.dbuff.service.replay.analysis.outlier;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShareRunsTest {

  @Test
  void of_returnsTheStretchesOnTheRightSideThatLastLongEnough() {
    double[] shares = {0.1, 0.4, 0.5, 0.45, 0.2, 0.4, 0.4, 0.1};

    List<ShareRuns.Run> runs = ShareRuns.of(shares, s -> s > 0.35, 3, true);

    assertThat(runs).hasSize(1);
    assertThat(runs.getFirst().startMinute()).isEqualTo(1);
    assertThat(runs.getFirst().endMinute()).isEqualTo(3);
    assertThat(runs.getFirst().extreme()).isEqualTo(0.5);
    assertThat(runs.getFirst().minutes()).isEqualTo(3);
  }

  @Test
  void of_takesTheLowestShareForALowThreshold() {
    double[] shares = {0.05, 0.02, 0.04, 0.3};

    assertThat(ShareRuns.of(shares, s -> s < 0.1, 3, false))
        .extracting(ShareRuns.Run::extreme)
        .containsExactly(0.02);
  }
}
