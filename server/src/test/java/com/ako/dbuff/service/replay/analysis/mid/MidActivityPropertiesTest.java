package com.ako.dbuff.service.replay.analysis.mid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MidActivityPropertiesTest {
  @Test
  void theDefaultsAreValid() {
    assertThatCode(() -> new MidActivityProperties().validate()).doesNotThrowAnyException();
  }

  @Test
  void aZeroWindowIsRefusedWithItsName() {
    MidActivityProperties properties = new MidActivityProperties();
    properties.setKillWindowSeconds(0);

    assertThatThrownBy(properties::validate).hasMessageContaining("kill-window-seconds");
  }

  @Test
  void aNegativeKillCountIsRefusedWithItsName() {
    MidActivityProperties properties = new MidActivityProperties();
    properties.setActiveMinKills(-1);

    assertThatThrownBy(properties::validate).hasMessageContaining("active-min-kills");
  }
}
