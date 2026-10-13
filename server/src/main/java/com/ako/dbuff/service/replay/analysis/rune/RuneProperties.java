package com.ako.dbuff.service.replay.analysis.rune;

import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.runes.*}; first guesses for the review rounds. The "opening" is
 * the window around the first (0:00) bounty-rune spawn; the contest for the power runes is read in
 * the pickups and in the fight reasons, not in the opening.
 */
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

  /** A pickup before this second counts as a mid laner's when his main early lane is MID. */
  private int midLanePickupSeconds = 600;

  /** A hero whose main lane in the first this-many seconds is MID counts as a mid laner. */
  private int midLaneEarlySeconds = 240;

  /**
   * How long the effect of a rune lasts, in seconds, used when the combat log has no modifier span
   * for the pickup (double damage 45, haste 22, arcane 50, invisibility 45, shield 75).
   */
  private Map<RuneType, Integer> effectSeconds =
      new EnumMap<>(
          Map.of(
              RuneType.DOUBLE_DAMAGE, 45,
              RuneType.HASTE, 22,
              RuneType.ARCANE, 50,
              RuneType.INVISIBILITY, 45,
              RuneType.SHIELD, 75));

  /** The modifier events pair with a pickup when they begin this close to it, in seconds. */
  private double modifierMatchSeconds = 2.0;

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
