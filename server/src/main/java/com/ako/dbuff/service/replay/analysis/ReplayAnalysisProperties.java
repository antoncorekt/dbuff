package com.ako.dbuff.service.replay.analysis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.*}: the thresholds the analyzers' rules use. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis")
public class ReplayAnalysisProperties {
  /** A creep last hit this close in time to a sample counts the hero as farming then. */
  private int activityFarmWindowSeconds = 10;

  /** Average speed between two samples above which a hero without farming is travelling. */
  private int activityTravelUnitsPerSecond = 200;
}
