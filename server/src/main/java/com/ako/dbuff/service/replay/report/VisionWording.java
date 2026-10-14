package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The words of a team's vision at an important fight that the Discord section uses. A ward is the
 * only vision counted: towers, creeps and nearby heroes are not, so "no vision" means no ward.
 */
public final class VisionWording {
  private VisionWording() {}

  /** The verdicts stored on a row, in stored order; codes that are not known are left out. */
  public static List<VisionVerdict> verdicts(FightVisionDomain row) {
    if (row.getVerdict() == null || row.getVerdict().isBlank()) {
      return List.of();
    }
    return Arrays.stream(row.getVerdict().split(","))
        .map(code -> Enums.find(VisionVerdict.class, code.strip()))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * One sentence for the problems of a row: {@code Dire fought without vision: no ward of theirs
   * covered the fight}, {@code Radiant had no sentry or dust against Riki}, {@code Dire's only ward
   * was placed 20 seconds before the fight}; several problems join into one sentence.
   */
  public static String sentence(
      FightVisionDomain row, List<VisionVerdict> problems, Map<Long, String> heroNames) {
    String team = Prettify.team(row.getTeam());
    boolean noVision = problems.contains(VisionVerdict.NO_VISION);
    boolean noDetection = problems.contains(VisionVerdict.INVISIBLE_HERO_NO_DETECTION);
    boolean late = problems.contains(VisionVerdict.LATE_WARD);
    String detection = "had no sentry or dust against " + invisible(row, heroNames);
    String text;
    if (noVision) {
      text = team + " fought without vision: no ward of theirs covered the fight";
      text += noDetection ? ", and they " + detection : "";
    } else if (noDetection) {
      text = team + " " + detection;
    } else {
      return lateWard(row, team);
    }
    return late ? text + "; " + lateWard(row, team) : text;
  }

  private static String lateWard(FightVisionDomain row, String team) {
    Integer observers = row.getActiveObservers();
    Integer sentries = row.getActiveSentries();
    Integer seconds = row.getLateWardSeconds();
    String when = seconds == null ? "just before the fight" : before(seconds);
    boolean countsKnown = observers != null && sentries != null;
    if (countsKnown && observers + sentries == 1) {
      return "%s's only ward was placed %s".formatted(team, when);
    }
    if (countsKnown && observers == 1) {
      return "%s's only observer was placed %s".formatted(team, when);
    }
    if (observers != null && observers > 1) {
      return "%s's observers were all placed shortly before the fight%s"
          .formatted(team, seconds == null ? "" : ", the latest " + before(seconds));
    }
    return "%s's latest ward was placed %s".formatted(team, when);
  }

  private static String before(int seconds) {
    return "%d %s before the fight".formatted(seconds, seconds == 1 ? "second" : "seconds");
  }

  private static String invisible(FightVisionDomain row, Map<Long, String> heroNames) {
    List<String> names =
        BehaviourWording.groupSlots(row.getInvisibleHeroes()).stream()
            .map(slot -> BehaviourWording.hero(heroNames, slot))
            .toList();
    return names.isEmpty() ? "an invisible hero" : LaneWording.joinNames(names);
  }
}
