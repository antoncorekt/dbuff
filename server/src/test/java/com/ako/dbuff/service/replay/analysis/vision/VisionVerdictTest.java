package com.ako.dbuff.service.replay.analysis.vision;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionVerdictTest {
  private static TeamWards wards(int observers, int late) {
    return new TeamWards(
        observers,
        late,
        0,
        observers == 0 ? null : 100,
        observers == 0 ? null : 100,
        observers - late * 0.5,
        late == 0 ? null : 10,
        false,
        null);
  }

  @Test
  void noObserverNearTheFightIsNoVision() {
    assertThat(VisionVerdict.of(wards(0, 0), false, false))
        .containsExactly(VisionVerdict.NO_VISION);
  }

  @Test
  void anInvisibleEnemyWithoutDetectionIsFlagged_alsoWithVision() {
    assertThat(VisionVerdict.of(wards(1, 0), true, false))
        .containsExactly(VisionVerdict.INVISIBLE_HERO_NO_DETECTION);
    assertThat(VisionVerdict.of(wards(1, 0), true, true)).containsExactly(VisionVerdict.FINE);
  }

  @Test
  void noFullWeightObserverWithALateOneCoveringIsALateWard_whateverTheCount() {
    assertThat(VisionVerdict.of(wards(1, 1), false, false))
        .containsExactly(VisionVerdict.LATE_WARD);
    assertThat(VisionVerdict.of(wards(2, 2), false, false))
        .as("two late wards are still no full ward")
        .containsExactly(VisionVerdict.LATE_WARD);
    assertThat(VisionVerdict.of(wards(2, 1), false, false)).containsExactly(VisionVerdict.FINE);
  }

  @Test
  void everyVerdictThatAppliesIsListedInOrder() {
    assertThat(VisionVerdict.of(wards(0, 0), true, false))
        .isEqualTo(List.of(VisionVerdict.NO_VISION, VisionVerdict.INVISIBLE_HERO_NO_DETECTION));
    assertThat(VisionVerdict.of(wards(1, 1), true, false))
        .isEqualTo(List.of(VisionVerdict.INVISIBLE_HERO_NO_DETECTION, VisionVerdict.LATE_WARD));
  }
}
