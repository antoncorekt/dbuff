package com.ako.dbuff.service.replay.analysis.importance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.important-fights.*}; first-guess values for the review rounds.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.important-fights")
public class ImportantFightProperties {
  /** A fight is important when the winner's gold advantage grew by this share of its net worth. */
  private double minSharePercent = 3;

  /** Or when it grew by this much gold. */
  private long minGold = 1500;

  /** How long after the fight the advantage is still read, to cover the one-minute resolution. */
  private int lagSeconds = 60;

  /** A fight shorter than this is measured as if it lasted this long, plus the lag. */
  private int minWindowSeconds = 60;

  /** A slope break needs the advantage to move at least this much on both sides of the fight. */
  private long minSlopeGold = 300;
}
