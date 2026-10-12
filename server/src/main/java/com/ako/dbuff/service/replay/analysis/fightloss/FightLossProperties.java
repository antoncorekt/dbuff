package com.ako.dbuff.service.replay.analysis.fightloss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.fight-loss.*}; first-guess values for the review rounds. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.fight-loss")
public class FightLossProperties {
  /**
   * Team reasons rank below player reasons, unless no player reason reaches this severity; then
   * every reason is ranked by severity alone.
   */
  private double minPlayerSeverity = 0.3;

  /** How far apart a replay death and OpenDota's death of the same hero may be, in seconds. */
  private double deathMatchSeconds = 3;

  /**
   * A fight that starts before this many seconds leaves its cooldown reasons out of the ranking.
   */
  private int earlyFightSeconds = 300;
}
