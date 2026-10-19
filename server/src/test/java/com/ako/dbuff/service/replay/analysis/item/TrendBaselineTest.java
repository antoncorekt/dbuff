package com.ako.dbuff.service.replay.analysis.item;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrendBaselineTest {
  @Test
  void aRisingSeriesIsExtendedAlongItsLine() {
    double[] series = {100, 110, 120, 130, 140, 999, 999};

    TrendBaseline baseline = TrendBaseline.fit(series, 5, 8);

    assertThat(baseline.trusted()).isTrue();
    assertThat(baseline.at(5)).isCloseTo(150, within(1e-9));
    assertThat(baseline.total(5, 7)).isCloseTo(150 + 160, within(1e-9));
  }

  @Test
  void onlyTheMinutesInsideTheTrendWindowAreFitted() {
    double[] series = {900, 900, 100, 100, 100, 100, 5000};

    TrendBaseline baseline = TrendBaseline.fit(series, 6, 4);

    assertThat(baseline.at(6)).isCloseTo(100, within(1e-9));
  }

  @Test
  void aFallingLineNeverGoesBelowZero() {
    TrendBaseline baseline = TrendBaseline.fit(new double[] {30, 20, 10}, 3, 8);

    assertThat(baseline.at(10)).isZero();
  }

  @Test
  void fewPointsAreFlatAndNotTrusted() {
    assertThat(TrendBaseline.fit(new double[] {70}, 1, 8).at(4)).isEqualTo(70);
    assertThat(TrendBaseline.fit(new double[] {70}, 1, 8).trusted()).isFalse();
    assertThat(TrendBaseline.fit(new double[0], 0, 8).at(4)).isZero();
  }
}
