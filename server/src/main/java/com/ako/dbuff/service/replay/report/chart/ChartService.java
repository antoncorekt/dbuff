package com.ako.dbuff.service.replay.report.chart;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Draws charts for reports at a bounded cost: finished PNGs are kept per key for a while, and only
 * a few charts are drawn at once. A chart that cannot be drawn yields nothing, so a report can say
 * so in words instead of failing.
 */
@Slf4j
@Service
public class ChartService {
  private final ChartRenderer renderer;
  private final Cache<String, byte[]> cache;
  private final Semaphore permits;
  private final long waitSeconds;

  public ChartService(ChartRenderer renderer, ChartProperties properties) {
    this.renderer = renderer;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(properties.getCacheMaxEntries())
            .expireAfterWrite(Duration.ofMinutes(properties.getCacheTtlMinutes()))
            .build();
    this.permits = new Semaphore(properties.getMaxConcurrent());
    this.waitSeconds = properties.getWaitSeconds();
  }

  /**
   * The PNG for the key, from the cache or drawn from the spec; empty when drawing failed or waited
   * too long for its turn. The key must say everything the spec depends on.
   */
  public Optional<byte[]> png(String key, Supplier<ChartSpec> spec) {
    byte[] cached = cache.getIfPresent(key);
    if (cached != null) {
      return Optional.of(cached);
    }
    try {
      if (!permits.tryAcquire(waitSeconds, TimeUnit.SECONDS)) {
        log.warn("Chart {} not drawn: no drawing slot free in {} s", key, waitSeconds);
        return Optional.empty();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
    try {
      byte[] png = cache.get(key, k -> renderer.render(spec.get()));
      return Optional.of(png);
    } catch (RuntimeException | LinkageError e) {
      log.warn("Chart {} could not be drawn: {}", key, e.toString(), e);
      return Optional.empty();
    } finally {
      permits.release();
    }
  }
}
