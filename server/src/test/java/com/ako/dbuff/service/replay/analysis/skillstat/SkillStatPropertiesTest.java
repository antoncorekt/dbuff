package com.ako.dbuff.service.replay.analysis.skillstat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillStatPropertiesTest {

  @Test
  void theWindowDefaultsToTwelveMonths() {
    assertThat(new SkillStatProperties().getWindowMonths()).isEqualTo(12);
  }

  @Test
  void oneToSixtyMonthsAreAccepted() {
    for (int months : new int[] {1, 12, 60}) {
      SkillStatProperties properties = new SkillStatProperties();
      properties.setWindowMonths(months);
      assertThatCode(properties::validate).doesNotThrowAnyException();
    }
  }

  @Test
  void anythingOutsideIsRefusedAtStartUp() {
    for (int months : new int[] {-1, 0, 61}) {
      SkillStatProperties properties = new SkillStatProperties();
      properties.setWindowMonths(months);
      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("window-months")
          .hasMessageContaining(String.valueOf(months));
    }
  }
}
