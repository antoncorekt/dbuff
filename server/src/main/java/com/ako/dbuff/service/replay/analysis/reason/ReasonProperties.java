package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.analysis.death.DeathReason;
import com.ako.dbuff.service.replay.analysis.fight.AbsenceReason;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.reasons.*}. Every number is a first-guess hypothesis of how
 * much a reason matters, to be tuned in the review rounds; a reason is stored whatever its
 * severity, and ranking or capping is left to the reader of the rows.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.reasons")
public class ReasonProperties {
  /** Used for a reason code that has no entry below. */
  private double defaultSeverity = 0.3;

  private Map<AbsenceReason, Double> absenceSeverity =
      new EnumMap<>(
          Map.of(
              AbsenceReason.NO_TP, 0.7,
              AbsenceReason.TP_ON_COOLDOWN, 0.5,
              AbsenceReason.HAD_TP_NOT_USED, 0.8));

  /** An ability or item that could not be cast because it was cooling down. */
  private double onCooldownSeverity = 0.4;

  private double noManaSeverity = 0.5;

  /** Added for each further ability of the same hero and reason in the fight. */
  private double repeatedUseStep = 0.1;

  /** Multiplies an ability reason of a hero who was near the fight but took no part in it. */
  private double passivePresenceFactor = 0.5;

  /** Reasons the death analysis gave; REINCARNATED and OTHER explain nothing and are left out. */
  private Map<DeathReason, Double> deathSeverity =
      new EnumMap<>(
          Map.ofEntries(
              Map.entry(DeathReason.DIED_IMMUNE, 0.1),
              Map.entry(DeathReason.BKB_PIERCED, 0.2),
              Map.entry(DeathReason.BKB_EXPIRED, 0.4),
              Map.entry(DeathReason.BKB_READY_UNUSED, 0.8),
              Map.entry(DeathReason.BKB_ON_COOLDOWN, 0.5),
              Map.entry(DeathReason.DISABLE_CHAIN, 0.3),
              Map.entry(DeathReason.CAUGHT_OUT, 0.7),
              Map.entry(DeathReason.TOWER, 0.4),
              Map.entry(DeathReason.BURST, 0.2),
              Map.entry(DeathReason.OUT_OF_POSITION, 0.7),
              Map.entry(DeathReason.NO_BKB, 0.3)));

  /** The first hero to die in a fight with at least {@link #diedFirstMinDeaths} deaths. */
  private double diedFirstSeverity = 0.5;

  private int diedFirstMinDeaths = 2;

  public double severity(AbsenceReason reason) {
    return absenceSeverity.getOrDefault(reason, defaultSeverity);
  }

  public double severity(DeathReason reason) {
    return deathSeverity.getOrDefault(reason, defaultSeverity);
  }
}
