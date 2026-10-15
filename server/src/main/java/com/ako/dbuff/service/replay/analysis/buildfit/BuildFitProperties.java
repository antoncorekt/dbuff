package com.ako.dbuff.service.replay.analysis.buildfit;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.build-fit.*}: when a missing function counts as a problem. All
 * values are first guesses (hypotheses) for the learning loop.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.build-fit")
public class BuildFitProperties {
  /**
   * Every role wants an escape; a role whose needs do not list one needs at least this much of it,
   * and it is only a problem when the player died alone.
   */
  private double escapeBonusMinimum = 0.3;

  /** The deaths alone (no ally in range, at least two enemies) that back a missing escape. */
  private int minLonelyDeaths = 2;

  /** The team net worth place, 1 to 5 with 5 the lowest, from which a missing farm skill counts. */
  private double lowNetWorthPlace = 4;

  @PostConstruct
  public void validate() {
    if (!(escapeBonusMinimum > 0 && escapeBonusMinimum <= 1)) {
      throw new IllegalStateException(
          "replay.analysis.build-fit.escape-bonus-minimum must be in (0, 1], got "
              + escapeBonusMinimum);
    }
    if (minLonelyDeaths < 1) {
      throw new IllegalStateException(
          "replay.analysis.build-fit.min-lonely-deaths must be at least 1, got " + minLonelyDeaths);
    }
    if (!(lowNetWorthPlace >= 1 && lowNetWorthPlace <= 5)) {
      throw new IllegalStateException(
          "replay.analysis.build-fit.low-net-worth-place must be in 1 to 5, got "
              + lowNetWorthPlace);
    }
  }
}
