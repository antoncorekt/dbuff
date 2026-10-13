package com.ako.dbuff.service.replay.analysis.vision;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionVerdictTest {
  private static final VisionProperties P = new VisionProperties();

  private static TeamWards wards(int observers, double weight, Integer late) {
    return new TeamWards(
        observers,
        0,
        observers == 0 ? null : 100,
        observers == 0 ? null : 100,
        weight,
        late,
        false);
  }

  @Test
  void noObserverNearTheFightIsNoVision() {
    assertThat(VisionVerdict.of(wards(0, 0, null), false, false, P))
        .containsExactly(VisionVerdict.NO_VISION);
  }

  @Test
  void anInvisibleEnemyWithoutDetectionIsFlagged_alsoWithVision() {
    assertThat(VisionVerdict.of(wards(1, 1, null), true, false, P))
        .containsExactly(VisionVerdict.INVISIBLE_HERO_NO_DETECTION);
    assertThat(VisionVerdict.of(wards(1, 1, null), true, true, P))
        .containsExactly(VisionVerdict.FINE);
  }

  @Test
  void aSingleLateObserverIsALateWard_butTwoLateOnesWeighEnough() {
    assertThat(VisionVerdict.of(wards(1, 0.5, 10), false, false, P))
        .containsExactly(VisionVerdict.LATE_WARD);
    assertThat(VisionVerdict.of(wards(2, 1.0, 10), false, false, P))
        .containsExactly(VisionVerdict.FINE);
    assertThat(VisionVerdict.of(wards(2, 1.5, 10), false, false, P))
        .containsExactly(VisionVerdict.FINE);
  }

  @Test
  void everyVerdictThatAppliesIsListedInOrder() {
    assertThat(VisionVerdict.of(wards(0, 0, null), true, false, P))
        .isEqualTo(List.of(VisionVerdict.NO_VISION, VisionVerdict.INVISIBLE_HERO_NO_DETECTION));
    assertThat(VisionVerdict.of(wards(1, 0.5, 5), true, false, P))
        .isEqualTo(List.of(VisionVerdict.INVISIBLE_HERO_NO_DETECTION, VisionVerdict.LATE_WARD));
  }
}
