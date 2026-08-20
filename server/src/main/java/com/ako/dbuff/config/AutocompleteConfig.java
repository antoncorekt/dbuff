package com.ako.dbuff.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rate limiting for Discord autocomplete lookups that reach OpenDota. */
@Configuration
public class AutocompleteConfig {

  /**
   * A budget for autocomplete searches, kept separate from {@code dotaApiRateLimiter}.
   *
   * <p>Autocomplete fires on every keystroke. Sharing the 60/min ingestion budget would let one
   * user typing in a search box starve match processing, so this is its own small allowance and
   * callers use {@code tryAcquire()} rather than blocking.
   *
   * @param permitsPerSec permitted searches per second
   * @return the limiter
   */
  @Bean("autocompleteSearchRateLimiter")
  public RateLimiter autocompleteSearchRateLimiter(
      @Value("${dbuff.autocomplete.search-permits-per-second:2.0}") double permitsPerSec) {
    return RateLimiter.create(permitsPerSec);
  }
}
