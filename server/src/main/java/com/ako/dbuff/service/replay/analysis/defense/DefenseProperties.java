package com.ako.dbuff.service.replay.analysis.defense;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.defense.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.defense")
public class DefenseProperties {
  private static final int MAX_WINDOW_MINUTES = 30;

  /** Enemy damage shares are taken over this many minutes before each sample. */
  private int windowMinutes = 5;

  /** Armor one point of agility gives. */
  private double armorPerAgility = 0.1667;

  @PostConstruct
  void validate() {
    if (windowMinutes < 1 || windowMinutes > MAX_WINDOW_MINUTES) {
      throw new IllegalStateException(
          "replay.analysis.defense.window-minutes must be 1 to "
              + MAX_WINDOW_MINUTES
              + ", not "
              + windowMinutes);
    }
    if (!(armorPerAgility > 0 && armorPerAgility <= 1)) {
      throw new IllegalStateException(
          "replay.analysis.defense.armor-per-agility must be above 0 and at most 1, not "
              + armorPerAgility);
    }
  }
}
