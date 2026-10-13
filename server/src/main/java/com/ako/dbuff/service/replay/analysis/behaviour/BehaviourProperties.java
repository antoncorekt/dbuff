package com.ako.dbuff.service.replay.analysis.behaviour;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.analysis.behaviour.*}; first-guess values for the review rounds. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.behaviour")
public class BehaviourProperties {
  /** Heroes of a team within this many world units of each other are one group. */
  private int clusterDistance = 2500;

  /** A group needs at least this many heroes; with fewer nobody counts as away. */
  private int minClusterSize = 2;

  /** A hero away from the group at least this long is on a trip. */
  private int awaySeconds = 20;

  /** The creep kills of a lane are compared this long after its hero left. */
  private int proxyWindowSeconds = 60;

  /** A kill or fight this long after a trip ended still belongs to it. */
  private int outcomeLingerSeconds = 15;

  /** The income of a trip is read this long after it ended too, to catch the walk back. */
  private int incomeTailSeconds = 60;

  /** A trip whose income per minute fell to this share of the one before it, or lower, dropped. */
  private double incomeShare = 0.7;

  @PostConstruct
  void validate() {
    if (minClusterSize < 2) {
      throw new IllegalStateException(
          "replay.analysis.behaviour.min-cluster-size must be at least 2, not " + minClusterSize);
    }
  }
}
