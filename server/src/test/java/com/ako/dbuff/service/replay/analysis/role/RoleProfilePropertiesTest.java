package com.ako.dbuff.service.replay.analysis.role;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleProfilePropertiesTest {

  @Test
  void theDefaultsAreComplete() {
    assertThatCode(() -> new RoleProfileProperties().validate()).doesNotThrowAnyException();
  }

  @Test
  void aWeightRowMissingAFunctionFailsAtStartupAndNamesIt() {
    RoleProfileProperties properties = new RoleProfileProperties();
    Map<RoleFunction, Double> partial = new EnumMap<>(RoleFunction.class);
    partial.put(RoleFunction.FARM_PRIORITY, 1.0);
    properties.getFit().getWeights().put(3, partial);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("role 3")
        .hasMessageContaining("JUNGLE");
  }

  @Test
  void aMissingRoleOrLaneBonusFailsAtStartup() {
    RoleProfileProperties noRole = new RoleProfileProperties();
    noRole.getFit().getWeights().remove(5);
    RoleProfileProperties noLane = new RoleProfileProperties();
    noLane.getFit().getLaneBonus().get(2).remove(LanePosition.OFF);

    assertThatThrownBy(noRole::validate).hasMessageContaining("role 5");
    assertThatThrownBy(noLane::validate).hasMessageContaining("role 2 and lane OFF");
    assertThat(noLane.getFit().getLaneBonus()).hasSize(5);
  }

  @Test
  void aFullShareOfZeroOrLessIsRefused() {
    RoleProfileProperties properties = new RoleProfileProperties();
    properties.getObserved().setWardFullShare(0);

    assertThatThrownBy(properties::validate).hasMessageContaining("full shares must be above 0");
  }
}
