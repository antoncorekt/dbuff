package com.ako.dbuff.service.replay.analysis.endgame;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.end-game.*}: the throw and buyback rules, first guesses. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.end-game")
public class EndGameProperties {
  private static final int MAX_SECONDS = 7200;

  /** The loser led by at least this share of both teams' net worth together to be able to throw. */
  private double throwLeadShare = 0.10;

  /** ...for at least this long. */
  private int throwMinSeconds = 120;

  /** Buybacks are counted over this many last seconds of the game. */
  private int buybackWindowSeconds = 600;

  @PostConstruct
  void validate() {
    if (!(throwLeadShare > 0 && throwLeadShare < 1)) {
      throw new IllegalStateException(
          "replay.analysis.end-game.throw-lead-share must be above 0 and below 1, not "
              + throwLeadShare);
    }
    requireSeconds("throw-min-seconds", throwMinSeconds);
    requireSeconds("buyback-window-seconds", buybackWindowSeconds);
  }

  private static void requireSeconds(String name, int value) {
    if (value < 1 || value > MAX_SECONDS) {
      throw new IllegalStateException(
          "replay.analysis.end-game." + name + " must be 1 to " + MAX_SECONDS + ", not " + value);
    }
  }
}
