package com.ako.dbuff.service.replay.analysis.skillstat;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.skill-stats.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.skill-stats")
public class SkillStatProperties {
  static final int MIN_WINDOW_MONTHS = 1;
  static final int MAX_WINDOW_MONTHS = 60;

  /** Ability Draft games in this many months before the match are counted. */
  private int windowMonths = 12;

  @PostConstruct
  void validate() {
    if (windowMonths < MIN_WINDOW_MONTHS || windowMonths > MAX_WINDOW_MONTHS) {
      throw new IllegalStateException(
          "replay.analysis.skill-stats.window-months must be "
              + MIN_WINDOW_MONTHS
              + " to "
              + MAX_WINDOW_MONTHS
              + ", not "
              + windowMonths);
    }
  }
}
