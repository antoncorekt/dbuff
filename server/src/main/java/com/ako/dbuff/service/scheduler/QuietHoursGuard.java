package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.config.QuietHoursProperties;
import com.ako.dbuff.dao.model.InstanceSettings;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides whether the current local time falls inside the configured quiet-hours window, during
 * which Dota-API-calling scheduler jobs should be skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuietHoursGuard {

  private final QuietHoursProperties properties;

  /**
   * Whether jobs for a channel should be skipped now, honouring that channel's own settings.
   *
   * <p>A channel with no quiet-hours choice follows the server-wide window; one that turned them
   * off never skips; one with its own window is judged by its own clock, in its own timezone.
   */
  public boolean isQuietTime(InstanceSettings settings) {
    return isQuietTime(settings, Instant.now());
  }

  /** For jobs that belong to no channel, so only the server-wide window applies. */
  public boolean isServerQuietTime() {
    return isQuietTime(LocalTime.now(ZoneId.systemDefault()));
  }

  boolean isQuietTime(InstanceSettings settings, Instant moment) {
    Boolean own = settings.getQuietHoursEnabled();
    if (Boolean.FALSE.equals(own)) {
      return false;
    }
    LocalTime now = LocalTime.ofInstant(moment, settings.zone());
    if (own == null) {
      return properties.isEnabled() && inWindow(now, properties.getStart(), properties.getEnd());
    }
    return inWindow(now, settings.getQuietStart(), settings.getQuietEnd());
  }

  /**
   * Tests a specific time against the server-wide window. Handles windows that wrap past midnight
   * (e.g. 23:00 to 07:00). The window is {@code [start, end)}.
   *
   * @param now the local time to test
   * @return {@code true} if {@code now} is within the quiet-hours window
   */
  boolean isQuietTime(LocalTime now) {
    return properties.isEnabled() && inWindow(now, properties.getStart(), properties.getEnd());
  }

  private static boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
    if (start == null || end == null || start.equals(end)) {
      return false;
    }
    if (start.isBefore(end)) {
      return !now.isBefore(start) && now.isBefore(end);
    }
    // Window wraps past midnight.
    return !now.isBefore(start) || now.isBefore(end);
  }
}
