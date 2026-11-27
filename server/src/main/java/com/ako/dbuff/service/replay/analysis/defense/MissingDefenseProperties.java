package com.ako.dbuff.service.replay.analysis.defense;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.defense.missing.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.defense.missing")
public class MissingDefenseProperties {
  private static final int MAX_WINDOW_MONTHS = 60;

  /** Enemy magical damage share from which a team without a magic item is reported. */
  private double magicShare = 0.55;

  /** Enemy physical damage share from which a team without a physical item is reported. */
  private double physicalShare = 0.55;

  /** Stored games needed on each side of the comparison. */
  private int minGames = 10;

  /** How many months before the match the stored games are taken from. */
  private int windowMonths = 12;

  @PostConstruct
  void validate() {
    requireShare("magic-share", magicShare);
    requireShare("physical-share", physicalShare);
    if (minGames < 1) {
      throw new IllegalStateException(
          "replay.analysis.defense.missing.min-games must be 1 or more, not " + minGames);
    }
    if (windowMonths < 1 || windowMonths > MAX_WINDOW_MONTHS) {
      throw new IllegalStateException(
          "replay.analysis.defense.missing.window-months must be 1 to "
              + MAX_WINDOW_MONTHS
              + ", not "
              + windowMonths);
    }
  }

  private static void requireShare(String key, double value) {
    if (!(value >= 0 && value <= 1)) {
      throw new IllegalStateException(
          "replay.analysis.defense.missing." + key + " must be 0 to 1, not " + value);
    }
  }
}
