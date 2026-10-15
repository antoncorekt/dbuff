package com.ako.dbuff.service.replay.analysis.mid;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.mid-activity.*}. The ACTIVE rule is a hypothesis, not a
 * finding: ACTIVE when the hero rotated and made at least {@code active-min-kills} kills after
 * leaving mid, or joined at least {@code min-fights-joined} fights and at least the team median
 * while his experience per minute beat the enemy mid's; otherwise PASSIVE.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.mid-activity")
public class MidActivityProperties {
  private static final int MAX_SECONDS = 3600;
  private static final int MAX_COUNT = 100;

  /** His first move is looked for in this many seconds from level 6. */
  private int nextWindowSeconds = 300;

  /** A lane or jungle stretch shorter than this is walking through, not leaving mid. */
  private int minAwaySeconds = 20;

  /** Kills count as made after leaving mid for this many seconds. */
  private int killWindowSeconds = 180;

  private int activeMinKills = 1;
  private int minFightsJoined = 1;

  @PostConstruct
  void validate() {
    requireRange("next-window-seconds", nextWindowSeconds, 1, MAX_SECONDS);
    requireRange("min-away-seconds", minAwaySeconds, 1, MAX_SECONDS);
    requireRange("kill-window-seconds", killWindowSeconds, 1, MAX_SECONDS);
    requireRange("active-min-kills", activeMinKills, 1, MAX_COUNT);
    requireRange("min-fights-joined", minFightsJoined, 1, MAX_COUNT);
  }

  private static void requireRange(String name, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalStateException(
          "replay.analysis.mid-activity."
              + name
              + " must be "
              + min
              + " to "
              + max
              + ", not "
              + value);
    }
  }
}
