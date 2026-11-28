package com.ako.dbuff.service.replay.report;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.report.defense.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.report.defense")
public class DefenseReportProperties {
  private static final int MAX_STEP_MINUTES = 10;

  /** The graph-defense section draws a point every this many game minutes. */
  private int stepMinutes = 2;

  @PostConstruct
  void validate() {
    if (stepMinutes < 1 || stepMinutes > MAX_STEP_MINUTES) {
      throw new IllegalStateException(
          "replay.report.defense.step-minutes must be 1 to "
              + MAX_STEP_MINUTES
              + ", not "
              + stepMinutes);
    }
  }
}
