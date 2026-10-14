package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs of the current parser version by status, read from {@code replay_parse_run} with one grouped
 * query and kept for {@link #TTL}, so any number of scrapes costs the database one query per
 * interval. A failing read keeps the last counts instead of throwing into the scraper.
 */
@Slf4j
@Component
public class ReplayRunCounts {
  static final Duration TTL = Duration.ofSeconds(30);

  private final ReplayParseRunRepo runRepo;
  private final ReplayProcessingProperties properties;
  private final Clock clock;

  private record Snapshot(Map<ReplayStatus, Long> counts, long loadedAtMillis) {}

  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
  private final ReentrantLock refreshing = new ReentrantLock();

  @Autowired
  public ReplayRunCounts(ReplayParseRunRepo runRepo, ReplayProcessingProperties properties) {
    this(runRepo, properties, Clock.systemUTC());
  }

  ReplayRunCounts(ReplayParseRunRepo runRepo, ReplayProcessingProperties properties, Clock clock) {
    this.runRepo = runRepo;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * One caller refreshes at a time; the others, and a refresh that fails, serve the last counts.
   */
  public long countOf(ReplayStatus status) {
    Snapshot current = snapshot.get();
    if ((current == null || isStale(current)) && refreshing.tryLock()) {
      try {
        current = snapshot.get();
        if (current == null || isStale(current)) {
          current = reload(current);
        }
      } finally {
        refreshing.unlock();
      }
    }
    return current == null ? 0L : current.counts().getOrDefault(status, 0L);
  }

  private boolean isStale(Snapshot current) {
    return clock.millis() - current.loadedAtMillis() >= TTL.toMillis();
  }

  private Snapshot reload(Snapshot last) {
    try {
      Map<ReplayStatus, Long> fresh = new EnumMap<>(ReplayStatus.class);
      for (var row : runRepo.countByStatus(properties.getParserVersion())) {
        fresh.put(row.getStatus(), row.getTotal());
      }
      Snapshot loaded = new Snapshot(fresh, clock.millis());
      snapshot.set(loaded);
      return loaded;
    } catch (RuntimeException e) {
      log.warn("Replay run counts could not be read, keeping the last ones: {}", e.getMessage());
      return last;
    }
  }
}
