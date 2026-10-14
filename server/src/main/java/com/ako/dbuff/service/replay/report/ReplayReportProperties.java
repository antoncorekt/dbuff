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
  private static final int MAX_IMPORTANT_FIGHT_LINES = 15;
  private static final int MIN_LANE_LINE_LENGTH = 60;
  private static final int MAX_LANE_LINE_LENGTH = 600;
  private static final double MAX_LANE_MARGIN = 2;
  private static final int MAX_BEHAVIOUR_LINES = 10;

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

  /** The margin of a lane from which its winner is said to have won clearly. */
  private double laneClearMargin = LaneWording.CLEAR_MARGIN;

  /** The margin from which a lane was won by a lot; smaller margins are narrow wins. */
  private double laneBigMargin = LaneWording.BIG_MARGIN;

  /** The longest a lane's line may be; a longer one ends in an ellipsis. */
  private int maxLaneLineLength = 400;

  /** The most lines the team rotations section lists. */
  private int maxRotationLines = 4;

  /** The most lines the smoke section lists. */
  private int maxSmokeLines = 3;

  /** The longest a rotation or smoke line may be; a longer one ends in an ellipsis. */
  private int maxBehaviourLineLength = 400;

  /** How far back a finished replay run is looked at for an analysis not yet posted. */
  private int lookbackHours = 48;

  /** The per-section switches by section name; a section without one follows its own default. */
  private Map<String, SectionToggle> sections = new HashMap<>();

  /**
   * Fails at startup on an important fights cap that leaves the section empty or too long for a
   * message, and on lane settings that cannot cut a line or tell narrow from clear wins.
   */
  @PostConstruct
  public void validate() {
    requireRange("max-important-fight-lines", maxImportantFightLines, 1, MAX_IMPORTANT_FIGHT_LINES);
    requireRange("max-rotation-lines", maxRotationLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("max-smoke-lines", maxSmokeLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange(
        "max-behaviour-line-length",
        maxBehaviourLineLength,
        MIN_LANE_LINE_LENGTH,
        MAX_LANE_LINE_LENGTH);
    requireRange(
        "max-lane-line-length", maxLaneLineLength, MIN_LANE_LINE_LENGTH, MAX_LANE_LINE_LENGTH);
    if (!(laneClearMargin > 0
        && laneClearMargin < laneBigMargin
        && laneBigMargin <= MAX_LANE_MARGIN)) {
      throw new IllegalStateException(
          "replay.report.lane-clear-margin and lane-big-margin must satisfy 0 < clear < big <= "
              + MAX_LANE_MARGIN
              + ", got "
              + laneClearMargin
              + " and "
              + laneBigMargin);
    }
  }

  private static void requireRange(String name, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalStateException(
          "replay.report." + name + " must be " + min + " to " + max + ", got " + value);
    }
  }

  @Data
  public static class SectionToggle {
    private Boolean enabled;
  }
}
