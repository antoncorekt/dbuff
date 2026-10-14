package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReasonPropertiesTest {
  @Test
  void theDefaultsAreValid() {
    ReasonProperties properties = new ReasonProperties();

    assertThatCode(properties::validate).doesNotThrowAnyException();
    assertThat(properties.getRotationWindowSeconds()).isEqualTo(60);
    assertThat(properties.severity(VisionVerdict.LATE_WARD))
        .isLessThan(properties.severity(VisionVerdict.NO_VISION));
  }

  @Test
  void aVisionVerdictWithoutAValueIsRefused() {
    ReasonProperties properties = new ReasonProperties();
    properties.setVisionSeverity(new EnumMap<>(VisionVerdict.class));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("vision-severity")
        .hasMessageContaining("NO_VISION");
  }

  @Test
  void aSeverityOutsideZeroToOneIsRefused() {
    ReasonProperties properties = new ReasonProperties();
    properties.getRotationSeverity().put(RotationVerdict.DIED, 1.5);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rotation-severity")
        .hasMessageContaining("DIED");
  }

  @Test
  void aNegativeWindowIsRefused() {
    ReasonProperties properties = new ReasonProperties();
    properties.setRotationWindowSeconds(-1);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rotation-window-seconds");
  }

  @Test
  void nanAndInfiniteSeveritiesAreRefused() {
    for (double bad :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      ReasonProperties properties = new ReasonProperties();
      properties.getVisionSeverity().put(VisionVerdict.LATE_WARD, bad);

      assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
  }
}
