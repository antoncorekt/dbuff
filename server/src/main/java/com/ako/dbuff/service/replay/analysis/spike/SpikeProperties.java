package com.ako.dbuff.service.replay.analysis.spike;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.spike.*}: the key item rule of a swing, first guesses. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.spike")
public class SpikeProperties {
  private static final int MAX_WINDOW_SECONDS = 1800;

  /** An item completed up to this long before the swing starts can be its key item. */
  private int keyItemWindowSeconds = 240;

  /** An item must cost at least this much, in gold, to be a key item. */
  private double keyItemMinCost = 2000;

  /**
   * Replay item names that are never a key item; a trailing {@code *} matches any ending. They are
   * consumables, shards and wards that a hero gets without it being a strength upgrade.
   */
  private List<String> keyItemExcluded =
      List.of(
          "item_aghanims_shard",
          "item_ultimate_scepter_2",
          "item_moon_shard",
          "item_refresher_shard",
          "item_tpscroll",
          "item_ward_*",
          "item_smoke_of_deceit",
          "item_cheese",
          "item_aegis");

  @PostConstruct
  void validate() {
    if (keyItemWindowSeconds < 1 || keyItemWindowSeconds > MAX_WINDOW_SECONDS) {
      throw new IllegalStateException(
          "replay.analysis.spike.key-item-window-seconds must be 1 to "
              + MAX_WINDOW_SECONDS
              + ", not "
              + keyItemWindowSeconds);
    }
    if (!(keyItemMinCost > 0 && keyItemMinCost < Double.POSITIVE_INFINITY)) {
      throw new IllegalStateException(
          "replay.analysis.spike.key-item-min-cost must be a finite number above 0, not "
              + keyItemMinCost);
    }
    if (keyItemExcluded == null) {
      throw new IllegalStateException("replay.analysis.spike.key-item-excluded must not be null");
    }
  }

  /** Whether a replay item name is on the exclusion list. */
  public boolean excludes(String replayName) {
    return keyItemExcluded.stream()
        .anyMatch(
            e ->
                e.endsWith("*")
                    ? replayName.startsWith(e.substring(0, e.length() - 1))
                    : e.equals(replayName));
  }
}
