package com.ako.dbuff.service.replay.report;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
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
  private static final int MIN_LINE_LENGTH = 60;
  private static final int MAX_LINE_LENGTH = 600;
  private static final double MAX_LANE_MARGIN = 2;
  private static final int MAX_OPENING_RUNE_MINUTES = 30;
  private static final int MAX_BEHAVIOUR_LINES = 10;
  private static final int MAX_GRAPH_HEROES_PER_IMAGE = 3;
  private static final int MAX_GRAPH_IMAGES = 10;
  private static final double MAX_OBJECTIVE_DISTANCE = 20000;

  /** The server-wide switch; nothing is posted until it is turned on. */
  private boolean enabled = false;

  /** How the report is shown: plain text, or Discord Components V2 cards. */
  private ReportFormat format = ReportFormat.PLAIN;

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

  /** The most lines the items section lists. */
  private int maxItemLines = 4;

  /** How many heroes one picture of the farm and income graphs holds, one panel each. */
  private int graphHeroesPerImage = 2;

  /** The most pictures one hero graph posts, so a big focus group cannot flood the thread. */
  private int graphMaxImages = 5;

  /** The most lines the outliers section lists. */
  private int maxOutlierLines = 4;

  /** The most lines the objectives section lists for missed objectives. */
  private int maxObjectiveLines = 3;

  /** The most lines the vision section lists for fights with a vision problem. */
  private int maxVisionLines = 4;

  /** A missed objective's closest hero is said to be close to the tower within this many units. */
  private double objectiveCloseDistance = 1500;

  /** From this many units the closest hero is said to be far from the tower. */
  private double objectiveFarDistance = 4000;

  /** The longest a rotation or smoke line may be; a longer one ends in an ellipsis. */
  private int maxBehaviourLineLength = 400;

  /**
   * The longest a line of the opening and runes section may be; a longer one ends in an ellipsis.
   */
  private int maxOpeningLineLength = 400;

  /** The longest a line of the end game section may be; a longer one ends in an ellipsis. */
  private int maxEndGameLineLength = 400;

  /** The longest a line of the skill picks section may be; a longer one ends in an ellipsis. */
  private int maxSkillPickLineLength = 400;

  /** The mid lane runes of the opening section are those taken in this many first minutes. */
  private int openingRuneMinutes = 10;

  /** How far back a finished replay run is looked at for an analysis not yet posted. */
  private int lookbackHours = 48;

  /** The per-section switches by section name; a section without one follows its own default. */
  private Map<String, SectionToggle> sections = new HashMap<>();

  /**
   * Names of the sections that post automatically when a channel has no list of its own. Null (not
   * set) keeps each section on its own default; set, even empty, the list is the only automatic
   * switch.
   */
  private List<String> defaultSections;

  /**
   * Seconds a thread waits between on-demand {@code /match report} and {@code /match ai} requests;
   * 0 turns the wait off. Kept in memory, so it holds per running instance of the app.
   */
  private int onDemandCooldownSeconds = 30;

  /**
   * Fails at startup on an important fights cap that leaves the section empty or too long for a
   * message, and on lane settings that cannot cut a line or tell narrow from clear wins.
   */
  @PostConstruct
  public void validate() {
    if (format == null) {
      throw new IllegalStateException("replay.report.format must be PLAIN or COMPONENTS");
    }
    if (onDemandCooldownSeconds < 0) {
      throw new IllegalStateException(
          "replay.report.on-demand-cooldown-seconds must be 0 or more, got "
              + onDemandCooldownSeconds);
    }
    requireRange("max-important-fight-lines", maxImportantFightLines, 1, MAX_IMPORTANT_FIGHT_LINES);
    requireRange("max-rotation-lines", maxRotationLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("max-smoke-lines", maxSmokeLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("max-objective-lines", maxObjectiveLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("max-item-lines", maxItemLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("graph-heroes-per-image", graphHeroesPerImage, 1, MAX_GRAPH_HEROES_PER_IMAGE);
    requireRange("graph-max-images", graphMaxImages, 1, MAX_GRAPH_IMAGES);
    requireRange("max-outlier-lines", maxOutlierLines, 1, MAX_BEHAVIOUR_LINES);
    requireRange("max-vision-lines", maxVisionLines, 1, MAX_BEHAVIOUR_LINES);
    if (!(objectiveCloseDistance > 0
        && objectiveCloseDistance < objectiveFarDistance
        && objectiveFarDistance <= MAX_OBJECTIVE_DISTANCE)) {
      throw new IllegalStateException(
          "replay.report.objective-close-distance and objective-far-distance must satisfy 0 <"
              + " close < far <= "
              + MAX_OBJECTIVE_DISTANCE
              + ", got "
              + objectiveCloseDistance
              + " and "
              + objectiveFarDistance);
    }
    requireLineLength("max-behaviour-line-length", maxBehaviourLineLength);
    requireRange("opening-rune-minutes", openingRuneMinutes, 1, MAX_OPENING_RUNE_MINUTES);
    requireLineLength("max-opening-line-length", maxOpeningLineLength);
    requireLineLength("max-lane-line-length", maxLaneLineLength);
    requireLineLength("max-end-game-line-length", maxEndGameLineLength);
    requireLineLength("max-skill-pick-line-length", maxSkillPickLineLength);
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

  private static void requireLineLength(String name, int value) {
    requireRange(name, value, MIN_LINE_LENGTH, MAX_LINE_LENGTH);
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
