package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayProcessingPropertiesTest {

  @Test
  void defaults_keepTheBacklogOffAndUseTheOwnersPriorityWindows() {
    ReplayProcessingProperties properties = new ReplayProcessingProperties();

    assertThat(properties.getFreshWindowHours()).isEqualTo(6);
    assertThat(properties.getRetryPriorityHours()).isEqualTo(24);
    assertThat(properties.getRetryPriorityAttempts()).isEqualTo(2);
    assertThat(properties.getBacklog().isEnabled()).isFalse();
    assertThat(properties.isEnabled()).isFalse();
  }
}
