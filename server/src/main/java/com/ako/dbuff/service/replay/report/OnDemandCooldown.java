package com.ako.dbuff.service.replay.report;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A wait between on-demand report requests in one thread, so a thread cannot be filled by repeating
 * a command. Kept in memory: it is per running instance of the app and forgotten on restart.
 */
@Component
public class OnDemandCooldown {
  private final ReplayReportProperties properties;
  private final Clock clock;
  private final Map<String, Long> readyAtMillis = new ConcurrentHashMap<>();

  @Autowired
  public OnDemandCooldown(ReplayReportProperties properties) {
    this(properties, Clock.systemUTC());
  }

  OnDemandCooldown(ReplayReportProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Starts the wait for the thread unless one is running.
   *
   * @return 0 when the request may go ahead, else the whole seconds still to wait
   */
  public long acquire(String threadKey) {
    long waitMillis = properties.getOnDemandCooldownSeconds() * 1000L;
    if (waitMillis <= 0 || threadKey == null) {
      return 0;
    }
    long now = clock.millis();
    readyAtMillis.values().removeIf(readyAt -> readyAt <= now);
    long[] remaining = {0};
    readyAtMillis.compute(
        threadKey,
        (key, readyAt) -> {
          if (readyAt != null && readyAt > now) {
            remaining[0] = readyAt - now;
            return readyAt;
          }
          return now + waitMillis;
        });
    return remaining[0] == 0 ? 0 : (remaining[0] + 999) / 1000;
  }
}
