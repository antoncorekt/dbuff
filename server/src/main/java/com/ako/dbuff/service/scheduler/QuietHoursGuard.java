package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.config.QuietHoursProperties;
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
   * @return {@code true} if the server's current local time is within the quiet-hours window
   */
  public boolean isQuietTime() {
    return isQuietTime(LocalTime.now(ZoneId.systemDefault()));
  }

  /**
   * Tests a specific time against the window. Handles windows that wrap past midnight (e.g. 23:00
   * to 07:00). The window is {@code [start, end)}.
   *
   * @param now the local time to test
   * @return {@code true} if {@code now} is within the window
   */
  boolean isQuietTime(LocalTime now) {
    if (!properties.isEnabled()) {
      return false;
    }
    LocalTime start = properties.getStart();
    LocalTime end = properties.getEnd();
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
