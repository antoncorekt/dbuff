package com.ako.dbuff.service.replay.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayAnalysisPropertiesTest {
  private final ReplayAnalysisProperties properties = new ReplayAnalysisProperties();

  @Test
  void theDefaultsAreValid() {
    properties.validate();

    assertThat(properties.getIncomeBreakMinutes()).isEqualTo(3);
    assertThat(properties.getIncomeBreakShare()).isEqualTo(0.25);
  }

  @Test
  void rejectsMinutesOutOfRange() {
    properties.setIncomeBreakMinutes(0);
    assertThatThrownBy(properties::validate).hasMessageContaining("income-break-minutes");
    properties.setIncomeBreakMinutes(31);
    assertThatThrownBy(properties::validate).hasMessageContaining("income-break-minutes");
  }

  @Test
  void rejectsASharePastTheRangeAndNaN() {
    for (double bad : new double[] {0, -0.1, 5.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      properties.setIncomeBreakShare(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("income-break-share");
    }
  }
}
