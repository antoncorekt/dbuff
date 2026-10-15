package com.ako.dbuff.service.replay.analysis.buildfit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildFitPropertiesTest {

  @Test
  void theDefaultsAreValid() {
    assertThatCode(() -> new BuildFitProperties().validate()).doesNotThrowAnyException();
  }

  @Test
  void aBadValueNamesTheProperty() {
    BuildFitProperties escape = new BuildFitProperties();
    escape.setEscapeBonusMinimum(0);
    BuildFitProperties deaths = new BuildFitProperties();
    deaths.setMinLonelyDeaths(0);
    BuildFitProperties place = new BuildFitProperties();
    place.setLowNetWorthPlace(6);
    BuildFitProperties nan = new BuildFitProperties();
    nan.setLowNetWorthPlace(Double.NaN);

    assertThatThrownBy(escape::validate).hasMessageContaining("escape-bonus-minimum");
    assertThatThrownBy(deaths::validate).hasMessageContaining("min-lonely-deaths");
    assertThatThrownBy(place::validate).hasMessageContaining("low-net-worth-place");
    assertThatThrownBy(nan::validate).hasMessageContaining("low-net-worth-place");
  }
}
