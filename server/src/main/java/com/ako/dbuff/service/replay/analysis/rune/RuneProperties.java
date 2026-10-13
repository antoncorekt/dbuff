package com.ako.dbuff.service.replay.analysis.rune;

import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.runes.*}; first guesses for the review rounds. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.runes")
public class RuneProperties {
  /** The game second the first runes appear; bounty runes spawn at 0:00. */
  private int firstSpawnSeconds = 0;

  /** The opening window starts this long before the first spawn (the pre-game fight). */
  private int openingBeforeSeconds = 30;

  private int openingAfterSeconds = 90;

  /** A bounty gold entry this close to the pickup (seconds) belongs to it. */
  private double goldMatchSeconds = 1.0;

  /** A hero whose main lane in the first this-many seconds is MID counts as a mid laner. */
  private int midLaneEarlySeconds = 240;

  /**
   * How long the effect of a rune lasts, in seconds; the durations of the modifiers in the combat
   * log (double damage 45, haste 22, arcane 50, invisibility 45, shield 75).
   */
  private Map<RuneType, Integer> effectSeconds =
      new EnumMap<>(
          Map.of(
              RuneType.DOUBLE_DAMAGE, 45,
              RuneType.HASTE, 22,
              RuneType.ARCANE, 50,
              RuneType.INVISIBILITY, 45,
              RuneType.SHIELD, 75));

  public int openingStart() {
    return firstSpawnSeconds - openingBeforeSeconds;
  }

  public int openingEnd() {
    return firstSpawnSeconds + openingAfterSeconds;
  }

  /** Zero for a rune with no timed effect. */
  public int effectSeconds(RuneType type) {
    return effectSeconds.getOrDefault(type, 0);
  }
}
