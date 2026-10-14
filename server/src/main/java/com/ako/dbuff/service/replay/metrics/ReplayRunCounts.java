package com.ako.dbuff.service.replay.metrics;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
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

  private Map<ReplayStatus, Long> counts = Map.of();
  private long loadedAtMillis;
  private boolean loaded;

  @Autowired
  public ReplayRunCounts(ReplayParseRunRepo runRepo, ReplayProcessingProperties properties) {
    this(runRepo, properties, Clock.systemUTC());
  }

  ReplayRunCounts(ReplayParseRunRepo runRepo, ReplayProcessingProperties properties, Clock clock) {
    this.runRepo = runRepo;
    this.properties = properties;
    this.clock = clock;
  }

  public synchronized long countOf(ReplayStatus status) {
    long now = clock.millis();
    if (!loaded || now - loadedAtMillis >= TTL.toMillis()) {
      reload(now);
    }
    return counts.getOrDefault(status, 0L);
  }

  private void reload(long now) {
    loadedAtMillis = now;
    loaded = true;
    try {
      Map<ReplayStatus, Long> fresh = new EnumMap<>(ReplayStatus.class);
      for (var row : runRepo.countByStatus(properties.getParserVersion())) {
        fresh.put(row.getStatus(), row.getTotal());
      }
      counts = fresh;
    } catch (RuntimeException e) {
      log.warn("Replay run counts could not be read, keeping the last ones: {}", e.getMessage());
    }
  }
}
