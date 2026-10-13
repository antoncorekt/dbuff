package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;

/**
 * One team's wards around a fight at its start. A ward covers the fight when it is within its
 * radius plus the position tolerance of any of the team's heroes in it.
 *
 * @param observers observers alive at the start that cover the fight
 * @param lateObservers how many of them were placed shortly before the fight
 * @param sentries sentries alive at the start that cover the fight
 * @param youngestAge seconds since the placement of the youngest covering observer, null without
 * @param weight the covering observers, a late one counted at its reduced weight
 * @param lateSeconds seconds before the start that the latest covering late observer was placed
 * @param sentryPlacedAround a sentry covering the fight was placed shortly before or during it
 * @param nearestWardDistance units from the nearest observer alive at the start, covering or not,
 *     to the nearest of the team's heroes in the fight; null without an observer alive
 */
public record TeamWards(
    int observers,
    int lateObservers,
    int sentries,
    Integer youngestAge,
    Integer oldestAge,
    double weight,
    Integer lateSeconds,
    boolean sentryPlacedAround,
    Integer nearestWardDistance) {

  public boolean late() {
    return lateObservers > 0;
  }

  public int fullObservers() {
    return observers - lateObservers;
  }

  /**
   * @param points the world positions of the team's heroes in the fight at its start
   */
  public static TeamWards at(
      List<WardEventDomain> wards,
      TeamSide team,
      List<double[]> points,
      double start,
      double end,
      VisionProperties p) {
    int observers = 0;
    int late = 0;
    int sentries = 0;
    Integer youngest = null;
    Integer oldest = null;
    Integer lateSeconds = null;
    Integer nearest = null;
    double weight = 0;
    boolean sentryPlaced = false;
    for (WardEventDomain w : wards) {
      if (!ownedBy(w, team) || !isPlaced(w)) {
        continue;
      }
      double distance = distance(w, points);
      boolean covers = distance <= radius(w.getWardKind(), p) + p.getWardPositionTolerance();
      boolean alive = w.getPlacedTime() <= start && endOf(w) > start;
      if (covers
          && w.getWardKind() == WardKind.SENTRY
          && w.getPlacedTime() >= start - p.getUseLookbackSeconds()
          && w.getPlacedTime() <= end) {
        sentryPlaced = true;
      }
      if (!alive) {
        continue;
      }
      if (w.getWardKind() == WardKind.OBSERVER) {
        int d = (int) Math.round(distance);
        nearest = nearest == null ? d : Math.min(nearest, d);
      }
      if (!covers) {
        continue;
      }
      int age = (int) Math.round(start - w.getPlacedTime());
      if (w.getWardKind() == WardKind.SENTRY) {
        sentries++;
        continue;
      }
      observers++;
      youngest = youngest == null ? age : Math.min(youngest, age);
      oldest = oldest == null ? age : Math.max(oldest, age);
      if (age <= p.getLateWardSeconds()) {
        late++;
        weight += p.getLateWardWeight();
        lateSeconds = lateSeconds == null ? age : Math.min(lateSeconds, age);
      } else {
        weight += 1;
      }
    }
    return new TeamWards(
        observers, late, sentries, youngest, oldest, weight, lateSeconds, sentryPlaced, nearest);
  }

  /**
   * The wards of the other team that this team destroyed from {@code from} to {@code to}: a ward
   * the log says was killed was killed by the enemy of its owner.
   */
  public static List<WardEventDomain> destroyedBy(
      List<WardEventDomain> wards, TeamSide team, double from, double to) {
    return wards.stream()
        .filter(w -> w.getPlayerSlot() != null && !ownedBy(w, team))
        .filter(w -> w.getRemovalKind() == WardRemovalKind.KILLED)
        .filter(
            w ->
                w.getRemovedTime() != null
                    && w.getRemovedTime() >= from
                    && w.getRemovedTime() <= to)
        .toList();
  }

  private static boolean ownedBy(WardEventDomain w, TeamSide team) {
    return w.getPlayerSlot() != null && TeamSide.ofSlot(w.getPlayerSlot()) == team;
  }

  private static boolean isPlaced(WardEventDomain w) {
    return w.getPlacedTime() != null
        && w.getX() != null
        && w.getY() != null
        && w.getWardKind() != null;
  }

  /** The end of removal, or the expiry a ward without a logged removal is taken to have. */
  private static long endOf(WardEventDomain w) {
    return w.getRemovedTime() != null
        ? w.getRemovedTime()
        : w.getPlacedTime() + w.getWardKind().getLifetimeSeconds();
  }

  private static double distance(WardEventDomain w, List<double[]> points) {
    return points.stream()
        .mapToDouble(pt -> WardCoordinates.distance(w.getX(), w.getY(), pt[0], pt[1]))
        .min()
        .orElse(Double.MAX_VALUE);
  }

  private static int radius(WardKind kind, VisionProperties p) {
    return kind == WardKind.OBSERVER ? p.getObserverRadius() : p.getSentryRadius();
  }
}
