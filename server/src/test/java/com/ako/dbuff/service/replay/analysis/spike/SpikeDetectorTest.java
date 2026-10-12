package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.service.replay.analysis.spike.SpikeDetector.Spike;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpikeDetectorTest {

  private final SpikeDetector detector =
      new SpikeDetector(new SpikeDetector.Tuning(1500, 0.03, 3, 1000));

  @Test
  void detect_findsTheMinutesInWhichTheDifferenceMovedFarAndGoesOnWhileItKeepsMoving() {
    double[] difference = {0, 200, 400, 2400, 3000, 3100, 3200};

    List<Spike> spikes = detector.detect(difference, total(difference.length));

    assertThat(spikes).hasSize(1);
    Spike spike = spikes.getFirst();
    assertThat(spike.startMinute()).isZero();
    assertThat(spike.endMinute()).isEqualTo(6);
    assertThat(spike.delta()).isEqualTo(3200);
    assertThat(spike.directionChange()).isFalse();
  }

  @Test
  void detect_reportsASwingTheOtherWayAndAChangeOfHands() {
    double[] difference = {0, 100, -300, -2500, -2600, -2700, 500, 2500, 2600};

    List<Spike> spikes = detector.detect(difference, total(difference.length));

    assertThat(spikes).extracting(Spike::delta).allMatch(d -> Math.abs(d) >= 1500);
    assertThat(spikes).hasSize(2);
    assertThat(spikes.get(0).delta()).isEqualTo(-2800);
    assertThat(spikes.get(0).startMinute()).isEqualTo(1);
    assertThat(spikes.get(1).directionChange()).isTrue();
    assertThat(spikes.get(1).leadBefore()).isEqualTo(-2700);
  }

  @Test
  void detect_ignoresSlowDriftAndSmallWobbles() {
    double[] difference = {0, 400, 800, 1200, 1600, 2000, 2400};

    assertThat(detector.detect(difference, total(difference.length))).isEmpty();
  }

  private static double[] total(int length) {
    double[] total = new double[length];
    Arrays.fill(total, 20000);
    return total;
  }
}
