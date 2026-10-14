package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
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
  static final String LAST_HEAP = "dbuff.replay.last.heap.used.bytes";
  static final String REPORT_POSTED = "dbuff.replay.report.posted";
  static final String REPORT_SKIPPED = "dbuff.replay.report.skipped";

  private final MeterRegistry registry;
  private final AtomicLong lastHeapUsed = new AtomicLong();

  public ReplayMetrics(MeterRegistry registry, ReplayRunCounts counts) {
    this.registry = registry;
    safely(
        () -> {
          for (ReplayStatus status : ReplayStatus.values()) {
            Gauge.builder(RUNS, counts, c -> c.countOf(status))
                .tag("status", status.name())
                .description("Replay runs by status, read from replay_parse_run")
                .register(registry);
          }
          Gauge.builder(LAST_HEAP, lastHeapUsed, AtomicLong::get)
              .baseUnit("bytes")
              .description("JVM heap in use right after the last replay run")
              .register(registry);
        });
  }

  /** One finished replay run: its duration by final status, and the heap it left behind. */
  public void runFinished(ReplayStatus status, Duration duration) {
    safely(
        () -> {
          registry.timer(RUN_DURATION, "status", status.name()).record(duration);
          Runtime runtime = Runtime.getRuntime();
          lastHeapUsed.set(runtime.totalMemory() - runtime.freeMemory());
        });
  }

  public void reportPosted() {
    safely(() -> registry.counter(REPORT_POSTED).increment());
  }

  /** Counted once per decision, so a skip that is retried on every poll counts every time. */
  public void reportSkipped(String cause) {
    safely(() -> registry.counter(REPORT_SKIPPED, "cause", cause).increment());
  }

  private static void safely(Runnable recording) {
    try {
      recording.run();
    } catch (RuntimeException e) {
      log.warn("Replay metric not recorded: {}", e.toString());
    }
  }
}
