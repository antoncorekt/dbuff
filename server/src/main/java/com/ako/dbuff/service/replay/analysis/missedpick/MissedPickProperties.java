package com.ako.dbuff.service.replay.analysis.missedpick;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.missed-pick.*}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.missed-pick")
public class MissedPickProperties {
  /** A candidate must do more than this multiple of the taken skill's total damage. */
  private double minRatio = 1.10;

  /** A candidate whose damage is above this multiple of its patch average was likely carried. */
  private double carriedRatio = 2.0;

  @PostConstruct
  void validate() {
    if (!(carriedRatio > 1 && carriedRatio < Double.POSITIVE_INFINITY)) {
      throw new IllegalStateException(
          "replay.analysis.missed-pick.carried-ratio must be a finite number above 1, not "
              + carriedRatio);
    }
    if (!(minRatio > 1 && minRatio < Double.POSITIVE_INFINITY)) {
      throw new IllegalStateException(
          "replay.analysis.missed-pick.min-ratio must be a finite number above 1, not " + minRatio);
    }
  }
}
