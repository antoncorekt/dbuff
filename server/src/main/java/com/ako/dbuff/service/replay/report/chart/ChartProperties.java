package com.ako.dbuff.service.replay.report.chart;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.report.chart.*}: the cost limits of drawing report charts. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.report.chart")
public class ChartProperties {
  private static final int MAX_CACHE_ENTRIES = 1000;
  private static final int MAX_CONCURRENT = 8;

  /** How many finished charts are kept; each is a PNG of some tens of kilobytes. */
  private int cacheMaxEntries = 50;

  /** How long a finished chart is kept, in minutes. */
  private int cacheTtlMinutes = 60;

  /** How many charts may be drawn at the same time; the others wait. */
  private int maxConcurrent = 1;

  /** How long a chart waits for its turn, in seconds, before the section gives up. */
  private int waitSeconds = 20;

  @PostConstruct
  void validate() {
    require(
        cacheMaxEntries >= 1 && cacheMaxEntries <= MAX_CACHE_ENTRIES,
        "cache-max-entries",
        1,
        MAX_CACHE_ENTRIES);
    require(cacheTtlMinutes >= 1 && cacheTtlMinutes <= 24 * 60, "cache-ttl-minutes", 1, 24 * 60);
    require(
        maxConcurrent >= 1 && maxConcurrent <= MAX_CONCURRENT, "max-concurrent", 1, MAX_CONCURRENT);
    require(waitSeconds >= 1 && waitSeconds <= 300, "wait-seconds", 1, 300);
  }

  private static void require(boolean valid, String name, int min, int max) {
    if (!valid) {
      throw new IllegalStateException(
          "replay.report.chart." + name + " must be between " + min + " and " + max);
    }
  }
}
