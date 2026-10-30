package com.ako.dbuff.service.replay.analysis.importance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportantFightPropertiesTest {

  @Test
  void aNegativeMinimumFlipGoldIsRejected() {
    ImportantFightProperties properties = new ImportantFightProperties();
    properties.setMinFlipGold(-1);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("replay.analysis.important-fights.min-flip-gold must not be negative, not -1");
  }

  @Test
  void zeroAndTheDefaultAreAccepted() {
    ImportantFightProperties properties = new ImportantFightProperties();
    assertThat(properties.getMinFlipGold()).isEqualTo(1500);
    assertThatCode(properties::validate).doesNotThrowAnyException();

    properties.setMinFlipGold(0);
    assertThatCode(properties::validate).doesNotThrowAnyException();
  }
}
