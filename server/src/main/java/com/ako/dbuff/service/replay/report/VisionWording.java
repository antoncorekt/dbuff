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

  /** Whether the row names a problem: a known verdict other than fine. */
  public static boolean isProblem(FightVisionDomain row) {
    return verdicts(row).stream().anyMatch(v -> v != VisionVerdict.FINE);
  }

  /** Whether the row says no ward of the team covered the fight. */
  public static boolean hadNoVision(FightVisionDomain row) {
    return verdicts(row).contains(VisionVerdict.NO_VISION);
  }

  /**
   * One sentence for a problem row: {@code Dire fought without vision: no ward of theirs covered
   * the fight}, {@code Radiant had no sentry or dust against Riki}, {@code Dire's only ward was
   * placed 20 seconds before the fight}; several problems join into one sentence.
   */
  public static String sentence(FightVisionDomain row, Map<Long, String> heroNames) {
    String team = Prettify.team(row.getTeam());
    List<VisionVerdict> verdicts = verdicts(row);
    boolean noVision = verdicts.contains(VisionVerdict.NO_VISION);
    boolean noDetection = verdicts.contains(VisionVerdict.INVISIBLE_HERO_NO_DETECTION);
    boolean late = verdicts.contains(VisionVerdict.LATE_WARD);
    String detection = "had no sentry or dust against " + invisible(row, heroNames);
    if (noVision) {
      String head = team + " fought without vision: no ward of theirs covered the fight";
      return noDetection ? head + ", and they " + detection : head;
    }
    if (noDetection) {
      return late ? team + " " + detection + "; " + lateWard(row, team) : team + " " + detection;
    }
    return lateWard(row, team);
  }

  private static String lateWard(FightVisionDomain row, String team) {
    int observers = row.getActiveObservers() == null ? 0 : row.getActiveObservers();
    String when =
        row.getLateWardSeconds() == null
            ? "just before the fight"
            : "%d seconds before the fight".formatted(row.getLateWardSeconds());
    return observers > 1
        ? "%s's wards were all placed shortly before the fight, the latest %s".formatted(team, when)
        : "%s's only ward was placed %s".formatted(team, when);
  }

  private static String invisible(FightVisionDomain row, Map<Long, String> heroNames) {
    List<String> names =
        BehaviourWording.groupSlots(row.getInvisibleHeroes()).stream()
            .map(slot -> BehaviourWording.hero(heroNames, slot))
            .toList();
    return names.isEmpty() ? "an invisible hero" : LaneWording.joinNames(names);
  }
}
