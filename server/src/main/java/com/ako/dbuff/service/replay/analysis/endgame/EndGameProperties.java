package com.ako.dbuff.service.replay.analysis.endgame;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.end-game.*}: the lead, throw and buyback rules. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.end-game")
public class EndGameProperties {
  private static final int MAX_SECONDS = 7200;
  private static final int MAX_MINUTE = 120;

  /** The loser "led" only when its peak lead is at least this much gold... */
  private double minLeadGold = 2000;

  /** ...and at least this share of both teams' net worth together; both must hold. */
  private double minLeadShare = 0.03;

  /** The loser led by at least this share of both teams' net worth together to be able to throw. */
  private double throwLeadShare = 0.10;

  /** ...and by at least this much gold... */
  private double throwMinGold = 3000;

  /** ...for at least this long, in samples that start no earlier than this game minute. */
  private int throwMinSeconds = 120;

  private int throwMinMinute = 10;

  /** Buybacks are counted over this many last seconds of the game. */
  private int buybackWindowSeconds = 600;

  @PostConstruct
  void validate() {
    requireShare("min-lead-share", minLeadShare);
    requireShare("throw-lead-share", throwLeadShare);
    requireGold("min-lead-gold", minLeadGold);
    requireGold("throw-min-gold", throwMinGold);
    requireSeconds("throw-min-seconds", throwMinSeconds);
    requireSeconds("buyback-window-seconds", buybackWindowSeconds);
    if (throwMinMinute < 0 || throwMinMinute > MAX_MINUTE) {
      throw new IllegalStateException(
          "replay.analysis.end-game.throw-min-minute must be 0 to "
              + MAX_MINUTE
              + ", not "
              + throwMinMinute);
    }
  }

  public EndGameLead.Rules leadRules() {
    return new EndGameLead.Rules(
        minLeadGold, minLeadShare, throwLeadShare, throwMinGold, throwMinMinute);
  }

  private static void requireShare(String name, double value) {
    if (!(value > 0 && value < 1)) {
      throw new IllegalStateException(
          "replay.analysis.end-game." + name + " must be above 0 and below 1, not " + value);
    }
  }

  private static void requireGold(String name, double value) {
    if (!(value >= 0 && value < Double.POSITIVE_INFINITY)) {
      throw new IllegalStateException(
          "replay.analysis.end-game." + name + " must be a finite number from 0, not " + value);
    }
  }

  private static void requireSeconds(String name, int value) {
    if (value < 1 || value > MAX_SECONDS) {
      throw new IllegalStateException(
          "replay.analysis.end-game." + name + " must be 1 to " + MAX_SECONDS + ", not " + value);
    }
  }
}
