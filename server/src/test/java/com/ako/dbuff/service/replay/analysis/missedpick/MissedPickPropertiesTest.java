package com.ako.dbuff.service.replay.analysis.missedpick;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissedPickPropertiesTest {
  @Test
  void theDefaultIsTenPercentAndValid() {
    MissedPickProperties properties = new MissedPickProperties();

    properties.validate();

    assertThat(properties.getMinRatio()).isEqualTo(1.10);
  }

  @Test
  void aRatioOfOneOrLessOrNotANumberIsRefused() {
    for (double bad : new double[] {1.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY}) {
      MissedPickProperties properties = new MissedPickProperties();
      properties.setMinRatio(bad);

      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("replay.analysis.missed-pick.min-ratio");
    }
  }
}
