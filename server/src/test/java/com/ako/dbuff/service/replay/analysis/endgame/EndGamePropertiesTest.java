package com.ako.dbuff.service.replay.analysis.endgame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndGamePropertiesTest {
  @Test
  void theDefaultsAreValid() {
    assertThatCode(() -> new EndGameProperties().validate()).doesNotThrowAnyException();
  }

  @Test
  void aShareOutsideZeroToOneOrNotANumberFailsStartUp() {
    for (double bad : new double[] {0, 1, -0.1, 1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
      EndGameProperties p = new EndGameProperties();
      p.setThrowLeadShare(bad);
      assertThatThrownBy(p::validate).hasMessageContaining("throw-lead-share");
    }
  }

  @Test
  void aSecondsSettingOutOfRangeFailsStartUp() {
    EndGameProperties minimum = new EndGameProperties();
    minimum.setThrowMinSeconds(0);
    assertThatThrownBy(minimum::validate).hasMessageContaining("throw-min-seconds");
    EndGameProperties window = new EndGameProperties();
    window.setBuybackWindowSeconds(100000);
    assertThatThrownBy(window::validate).hasMessageContaining("buyback-window-seconds");
  }
}
