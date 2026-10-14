package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The meters for watching replay processing. Metrics must never fail a replay run, so every method
 * swallows what the registry throws.
 */
@Slf4j
@Component
public class ReplayMetrics {
  static final String RUNS = "dbuff.replay.runs";
  static final String RUN_DURATION = "dbuff.replay.run.duration";
  static final String REPORT_POSTED = "dbuff.replay.report.posted";

  private final MeterRegistry registry;

  public ReplayMetrics(MeterRegistry registry, ReplayRunCounts counts) {
    this.registry = registry;
    safely(
        () -> {
          for (ReplayStatus status : ReplayStatus.values()) {
            Gauge.builder(RUNS, counts, c -> c.countOf(status))
                .tag("status", status.name())
                .description(
                    "Replay runs by status of the current parser version, read from"
                        + " replay_parse_run")
                .register(registry);
          }
        });
  }

  /** One replay run that took the parse permit: its duration by final status. */
  public void runFinished(ReplayStatus status, Duration duration) {
    safely(() -> registry.timer(RUN_DURATION, "status", status.name()).record(duration));
  }

  public void reportPosted() {
    safely(() -> registry.counter(REPORT_POSTED).increment());
  }

  private static void safely(Runnable recording) {
    try {
      recording.run();
    } catch (RuntimeException e) {
      log.warn("Replay metric not recorded: {}", e.toString());
    }
  }
}
