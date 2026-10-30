package com.ako.dbuff.service.replay.analysis.behaviour;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BehaviourPropertiesTest {
  private static void rejects(Consumer<BehaviourProperties> change, String name) {
    BehaviourProperties p = new BehaviourProperties();
    change.accept(p);
    assertThatThrownBy(p::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("replay.analysis.behaviour." + name);
  }

  @Test
  void validate_acceptsTheDefaults() {
    assertThatCode(new BehaviourProperties()::validate).doesNotThrowAnyException();
    assertThat(new BehaviourProperties().getSplitFallbackStartSeconds()).isEqualTo(720);
  }

  @Test
  void validate_rejectsValuesThatCannotWork() {
    rejects(p -> p.setMinClusterSize(1), "min-cluster-size");
    rejects(p -> p.setSplitFallbackStartSeconds(-1), "split-fallback-start-seconds");
    rejects(p -> p.setGroupedMinHeroes(1), "grouped-min-heroes");
    rejects(p -> p.setGroupedMinHeroes(6), "grouped-min-heroes");
    rejects(
        p -> {
          p.setMinClusterSize(3);
          p.setGroupedMinHeroes(2);
        },
        "grouped-min-heroes");
    rejects(p -> p.setPostureMinLivingHeroes(1), "posture-min-living-heroes");
    rejects(p -> p.setPostureMinLivingHeroes(6), "posture-min-living-heroes");
    rejects(p -> p.setSplitMinAloneHeroes(6), "split-min-alone-heroes");
    rejects(p -> p.setSplitMinAloneHeroes(1), "split-min-alone-heroes");
    rejects(p -> p.setMinStretchMinutes(0), "min-stretch-minutes");
    rejects(p -> p.setSwapToMinute(8), "swap-from-minute");
    rejects(p -> p.setSwapMinShare(0), "swap-min-share");
    rejects(p -> p.setSwapMinShare(1.5), "swap-min-share");
    rejects(p -> p.setSmokeGroupSeconds(-1), "smoke-group-seconds");
    rejects(p -> p.setSmokeExpirySlackSeconds(-0.5), "smoke-expiry-slack-seconds");
    rejects(p -> p.setSmokeOutcomeSeconds(0), "smoke-outcome-seconds");
    rejects(p -> p.setObjectiveWindowSeconds(0), "objective-window-seconds");
    rejects(p -> p.setRoshanNearRadius(0), "roshan-near-radius");
    rejects(p -> p.setRotationMinStartSeconds(-1), "rotation-min-start-seconds");
    rejects(p -> p.setObjectiveMinFightSeconds(-1), "objective-min-fight-seconds");
    rejects(p -> p.setObjectiveMaxTowerDistance(0), "objective-max-tower-distance");
  }
}
