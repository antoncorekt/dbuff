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
  void theLeadAndThrowMinimumsMustBeFiniteAndNotNegative() {
    for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
      EndGameProperties gold = new EndGameProperties();
      gold.setMinLeadGold(bad);
      assertThatThrownBy(gold::validate).hasMessageContaining("min-lead-gold");
      EndGameProperties throwGold = new EndGameProperties();
      throwGold.setThrowMinGold(bad);
      assertThatThrownBy(throwGold::validate).hasMessageContaining("throw-min-gold");
    }
    EndGameProperties share = new EndGameProperties();
    share.setMinLeadShare(Double.NaN);
    assertThatThrownBy(share::validate).hasMessageContaining("min-lead-share");
    EndGameProperties minute = new EndGameProperties();
    minute.setThrowMinMinute(-1);
    assertThatThrownBy(minute::validate).hasMessageContaining("throw-min-minute");
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
