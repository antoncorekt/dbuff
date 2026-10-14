package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayMetricsTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final ReplayRunCounts counts = mock(ReplayRunCounts.class);
  private final ReplayMetrics metrics = new ReplayMetrics(registry, counts);

  @Test
  void aRunsGaugeExistsForEveryStatusAndReadsTheCounts() {
    when(counts.countOf(ReplayStatus.DONE)).thenReturn(5L);

    for (ReplayStatus status : ReplayStatus.values()) {
      assertThat(registry.get("dbuff.replay.runs").tag("status", status.name()).gauge())
          .isNotNull();
    }
    assertThat(registry.get("dbuff.replay.runs").tag("status", "DONE").gauge().value())
        .isEqualTo(5.0);
    assertThat(registry.get("dbuff.replay.runs").tag("status", "PARTIAL").gauge().value()).isZero();
  }

  @Test
  void runFinished_recordsTheDurationUnderTheFinalStatusAndTheHeap() {
    metrics.runFinished(ReplayStatus.DONE, Duration.ofSeconds(4));
    metrics.runFinished(ReplayStatus.FAILED_PARSER, Duration.ofSeconds(1));
    metrics.runFinished(ReplayStatus.DONE, Duration.ofSeconds(2));

    var done = registry.get("dbuff.replay.run.duration").tag("status", "DONE").timer();
    assertThat(done.count()).isEqualTo(2);
    assertThat(done.totalTime(TimeUnit.SECONDS)).isEqualTo(6.0);
    assertThat(
            registry
                .get("dbuff.replay.run.duration")
                .tag("status", "FAILED_PARSER")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(registry.get("dbuff.replay.last.heap.used.bytes").gauge().value()).isPositive();
  }

  @Test
  void reportCounters_countPostedAndSkippedByCause() {
    metrics.reportPosted();
    metrics.reportSkipped("quiet_hours");
    metrics.reportSkipped("quiet_hours");
    metrics.reportSkipped("no_thread");

    assertThat(registry.get("dbuff.replay.report.posted").counter().count()).isEqualTo(1);
    assertThat(
            registry
                .get("dbuff.replay.report.skipped")
                .tag("cause", "quiet_hours")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(
            registry.get("dbuff.replay.report.skipped").tag("cause", "no_thread").counter().count())
        .isEqualTo(1);
  }

  @Test
  void recording_neverThrowsWhenTheRegistryDoes() {
    MeterRegistry broken = mock(MeterRegistry.class);
    when(broken.timer(any(String.class), any(String[].class)))
        .thenThrow(new IllegalStateException("registry closed"));
    when(broken.counter(any(String.class), any(String[].class)))
        .thenThrow(new IllegalStateException("registry closed"));

    assertThatCode(
            () -> {
              ReplayMetrics onBroken = new ReplayMetrics(broken, counts);
              onBroken.runFinished(ReplayStatus.DONE, Duration.ofSeconds(1));
              onBroken.reportPosted();
              onBroken.reportSkipped("quiet_hours");
            })
        .doesNotThrowAnyException();
  }
}
