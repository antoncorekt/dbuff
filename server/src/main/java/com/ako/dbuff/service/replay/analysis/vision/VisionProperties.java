package com.ako.dbuff.service.replay.analysis.vision;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.vision.*}; first-guess values for the review rounds. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.vision")
public class VisionProperties {
  /** The window of an important fight starts this long before the fight does. */
  private int windowBeforeSeconds = 90;

  /** The window ends this long after the fight does. */
  private int windowAfterSeconds = 15;

  /** An observer sees a hero of the fight within this many world units of it. */
  private int observerRadius = 1600;

  /** A sentry reveals invisible heroes within this many world units of it. */
  private int sentryRadius = 900;

  /** An observer placed this long before the fight, or less, is a late ward. */
  private int lateWardSeconds = 30;

  /** How much a late ward counts against 1 for an older one. */
  private double lateWardWeight = 0.5;

  /**
   * A ward covers a hero when it is within its radius plus this many world units of him: the
   * measured error of a ward's position (see {@link WardCoordinates}).
   */
  private int wardPositionTolerance = 620;

  /**
   * An invisibility ability or item used this long before the fight, or after its start, counts as
   * the hero being invisible in it; the same reach is used for dust and a sentry.
   */
  private int useLookbackSeconds = 15;
}
