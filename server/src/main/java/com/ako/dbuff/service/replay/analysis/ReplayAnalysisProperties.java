package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.analysis.activity.ActivityTuning;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.*}: the thresholds the analyzers' rules use. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis")
public class ReplayAnalysisProperties {
  /** A creep last hit up to this long before a sample, or during it, counts the hero as farming. */
  private int activityFarmLookbackSeconds = 10;

  /** Average speed between two samples above which a hero without farming is travelling. */
  private int activityTravelUnitsPerSecond = 200;

  /** A speed above this is a teleport or respawn jump, which is not travelling on foot. */
  private int activityJumpUnitsPerSecond = 700;

  public ActivityTuning activityTuning() {
    return new ActivityTuning(
        activityFarmLookbackSeconds, activityTravelUnitsPerSecond, activityJumpUnitsPerSecond);
  }
}
