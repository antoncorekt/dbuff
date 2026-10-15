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
   * The outcome of {@link #acquire}: {@code waitSeconds} is 0 when the request may go ahead. Only
   * the permit that started a wait can end it, so a slow request cannot cancel a newer one's wait.
   */
  public record Permit(String threadKey, long readyAtMillis, long waitSeconds) {
    public boolean granted() {
      return waitSeconds == 0;
    }
  }

  /** Ends the wait this permit started, if it is still the one running; a miss costs no wait. */
  public void release(Permit permit) {
    if (permit != null && permit.granted() && permit.threadKey() != null) {
      readyAtMillis.remove(permit.threadKey(), permit.readyAtMillis());
    }
  }

  /** Starts the wait for the thread unless one is running. */
  public Permit acquire(String threadKey) {
    long waitMillis = properties.getOnDemandCooldownSeconds() * 1000L;
    if (waitMillis <= 0 || threadKey == null) {
      return new Permit(null, 0, 0);
    }
    long now = clock.millis();
    readyAtMillis.values().removeIf(readyAt -> readyAt <= now);
    long[] remaining = {0};
    long[] started = {0};
    readyAtMillis.compute(
        threadKey,
        (key, readyAt) -> {
          if (readyAt != null && readyAt > now) {
            remaining[0] = readyAt - now;
            return readyAt;
          }
          started[0] = now + waitMillis;
          return started[0];
        });
    return new Permit(threadKey, started[0], (remaining[0] + 999) / 1000);
  }
}
