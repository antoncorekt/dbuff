package com.ako.dbuff.service.replay.analysis.defense;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefensePropertiesTest {

  @Test
  void defaultsAreValid() {
    assertThatCode(() -> new DefenseProperties().validate()).doesNotThrowAnyException();
  }

  @Test
  void windowMinutesZeroIsRejected() {
    DefenseProperties p = new DefenseProperties();
    p.setWindowMinutes(0);
    assertThatThrownBy(p::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("window-minutes");
  }

  @Test
  void windowMinutesAboveMaxIsRejected() {
    DefenseProperties p = new DefenseProperties();
    p.setWindowMinutes(31);
    assertThatThrownBy(p::validate).hasMessageContaining("window-minutes");
  }

  @Test
  void armorPerAgilityZeroIsRejected() {
    DefenseProperties p = new DefenseProperties();
    p.setArmorPerAgility(0);
    assertThatThrownBy(p::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("armor-per-agility");
  }

  @Test
  void armorPerAgilityAboveOneIsRejected() {
    DefenseProperties p = new DefenseProperties();
    p.setArmorPerAgility(1.5);
    assertThatThrownBy(p::validate).hasMessageContaining("armor-per-agility");
  }
}
