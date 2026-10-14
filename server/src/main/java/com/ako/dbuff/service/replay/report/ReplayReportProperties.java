package com.ako.dbuff.service.replay.report;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
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

  /** The longest a team's composition verdict line may be; a longer one ends in an ellipsis. */
  private int maxCompositionLineLength = 300;

  /** The most reasons a lost fight in the swings section gives. */
  private int maxReasonsPerFight = 3;

  /** The most lost fights one swing tells the reasons of. */
  private int maxLostFightsPerSwing = 2;

  /** The most lines the important fights section lists. */
  private int maxImportantFightLines = 6;

  /** How far back a finished replay run is looked at for an analysis not yet posted. */
  private int lookbackHours = 48;

  /** The per-section switches by section name; a section without one follows its own default. */
  private Map<String, SectionToggle> sections = new HashMap<>();

  /** Fails at startup on a cap that would leave the important fights section empty. */
  @PostConstruct
  public void validate() {
    if (maxImportantFightLines < 1) {
      throw new IllegalStateException(
          "replay.report.max-important-fight-lines must be at least 1, got "
              + maxImportantFightLines);
    }
  }

  @Data
  public static class SectionToggle {
    private Boolean enabled;
  }
}
