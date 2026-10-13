package com.ako.dbuff.service.replay.analysis.vision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WardCoordinatesTest {
  @Test
  void theCentreCellIsTheWorldOrigin_andACellIs128Units() {
    assertThat(WardCoordinates.toWorld(128)).isZero();
    assertThat(WardCoordinates.toWorld(129.5)).isEqualTo(192);
    assertThat(WardCoordinates.toWorld(64)).isEqualTo(-8192);
    assertThat(WardCoordinates.toWorld(192)).isEqualTo(8192);
  }

  /** Placements of the fixture matches with the placing hero's track position at that time. */
  @Test
  void aPlacementIsWithinTheCastRangeAndTrackStepOfThePlacingHero() {
    double[][] placements = {
      {124.1, 127.1, -338, -7},
      {132.8, 126.2, 493, -395},
      {94.6, 161.2, -4454, 4113},
      {162.3, 92.3, 4459, -4323},
      {85, 163.4, -5410, 4286},
      {120.8, 121.8, -975, -1053}
    };
    for (double[] p : placements) {
      assertThat(WardCoordinates.distance(p[0], p[1], p[2], p[3])).isLessThan(800);
    }
  }

  @Test
  void distanceIsEuclideanInWorldUnits() {
    assertThat(WardCoordinates.distance(129, 129, 0, 0))
        .isEqualTo(Math.hypot(128, 128), within(1e-9));
  }
}
