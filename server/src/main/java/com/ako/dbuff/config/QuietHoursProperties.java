package com.ako.dbuff.config;

import java.time.LocalTime;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Quiet-hours window (in the server's local time) during which Dota-API-calling scheduler jobs are
 * skipped. Defaults to 01:00–09:00. Bound from {@code scheduler.quiet-hours.*}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "scheduler.quiet-hours")
public class QuietHoursProperties {

  /** Whether the quiet-hours window is active. */
  private boolean enabled = true;

  /** Start of the window (inclusive), local time. */
  private LocalTime start = LocalTime.of(1, 0);

  /** End of the window (exclusive), local time. */
  private LocalTime end = LocalTime.of(9, 0);
}
