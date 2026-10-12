package com.ako.dbuff.service.replay.report;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code replay.report.*}: whether and how much of the replay analysis is posted. */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.report")
public class ReplayReportProperties {
  /** The server-wide switch; nothing is posted until it is turned on. */
  private boolean enabled = false;

  /** The most messages one match's replay analysis takes at the full detail level. */
  private int maxMessages = 4;

  /** The most messages it takes at the standard detail level. */
  private int standardMaxMessages = 2;

  /** The most findings one section lists. */
  private int maxFindingsPerSection = 3;

  /** How far back a finished replay run is looked at for an analysis not yet posted. */
  private int lookbackHours = 48;
}
