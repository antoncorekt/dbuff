package com.ako.dbuff.config;

import java.util.concurrent.Semaphore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for concurrency limits when using virtual threads.
 *
 * <p>Virtual threads can spawn thousands of concurrent tasks, but database connections are limited.
 * This configuration provides semaphores to limit concurrent database operations.
 */
@Slf4j
@Configuration
public class ConcurrencyConfig {

  /**
   * Semaphore to limit concurrent match processing operations. This prevents overwhelming the
   * database connection pool.
   */
  @Bean
  public Semaphore matchProcessingSemaphore(ConcurrencyProperties props) {
    log.info("Creating match processing semaphore with {} permits", props.getMaxParallelMatches());
    return new Semaphore(props.getMaxParallelMatches());
  }

  /** Semaphore to limit concurrent page scraping operations. */
  @Bean
  public Semaphore pageScrapingSemaphore(ConcurrencyProperties props) {
    log.info("Creating page scraping semaphore with {} permits", props.getMaxParallelPages());
    return new Semaphore(props.getMaxParallelPages());
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "app.concurrency")
  public static class ConcurrencyProperties {
    /**
     * Maximum number of matches to process in parallel. Each match processing requires database
     * connections.
     */
    private int maxParallelMatches = 50;

    /** Maximum number of pages to scrape in parallel. */
    private int maxParallelPages = 5;
  }
}
