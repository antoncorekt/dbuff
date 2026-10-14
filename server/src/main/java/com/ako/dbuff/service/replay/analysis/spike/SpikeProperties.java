package com.ako.dbuff.service.replay.analysis.spike;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.spike.*}: the key item rule of a swing, first guesses. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.spike")
public class SpikeProperties {
  /** An item completed up to this long before the swing starts can be its key item. */
  private int keyItemWindowSeconds = 240;

  /** An item must cost at least this much, in gold, to be a key item. */
  private double keyItemMinCost = 2000;

  @PostConstruct
  void validate() {
    if (keyItemWindowSeconds <= 0) {
      throw new IllegalStateException(
          "replay.analysis.spike.key-item-window-seconds must be positive, not "
              + keyItemWindowSeconds);
    }
    if (Double.isNaN(keyItemMinCost) || keyItemMinCost < 0) {
      throw new IllegalStateException(
          "replay.analysis.spike.key-item-min-cost must be a number of 0 or more, not "
              + keyItemMinCost);
    }
  }
}
