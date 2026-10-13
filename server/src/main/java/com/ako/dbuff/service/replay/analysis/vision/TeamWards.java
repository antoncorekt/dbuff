package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;

/**
 * One team's wards around a fight's centre at its start.
 *
 * @param youngestAge seconds since the placement of the youngest observer near, null without one
 * @param weight observers near the fight, a late one counted at its reduced weight
 * @param lateSeconds seconds before the start that the latest late observer was placed, null when
 *     none is late
 * @param sentryPlacedAround a sentry was placed near the fight shortly before or during it
 */
public record TeamWards(
    int observers,
    int sentries,
    Integer youngestAge,
    Integer oldestAge,
    double weight,
    Integer lateSeconds,
    boolean sentryPlacedAround) {

  public boolean late() {
    return lateSeconds != null;
  }

  /** The team's wards alive at {@code start} and within their radius of the fight's centre. */
  public static TeamWards at(
      List<WardEventDomain> wards,
      TeamSide team,
      double centerX,
      double centerY,
      double start,
      double end,
      VisionProperties p) {
    int observers = 0;
    int sentries = 0;
    Integer youngest = null;
    Integer oldest = null;
    Integer lateSeconds = null;
    double weight = 0;
    boolean sentryPlaced = false;
    for (WardEventDomain w : wards) {
      if (!ownedBy(w, team) || !isPlaced(w)) {
        continue;
      }
      boolean near = distance(w, centerX, centerY) <= radius(w.getWardKind(), p);
      if (near
          && w.getWardKind() == WardKind.SENTRY
          && w.getPlacedTime() >= start - p.getUseLookbackSeconds()
          && w.getPlacedTime() <= end) {
        sentryPlaced = true;
      }
      if (!near || w.getPlacedTime() > start || endOf(w) <= start) {
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
        weight += p.getLateWardWeight();
        lateSeconds = lateSeconds == null ? age : Math.min(lateSeconds, age);
      } else {
        weight += 1;
      }
    }
    return new TeamWards(observers, sentries, youngest, oldest, weight, lateSeconds, sentryPlaced);
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

  private static double distance(WardEventDomain w, double centerX, double centerY) {
    return WardCoordinates.distance(w.getX(), w.getY(), centerX, centerY);
  }

  private static int radius(WardKind kind, VisionProperties p) {
    return kind == WardKind.OBSERVER ? p.getObserverRadius() : p.getSentryRadius();
  }
}
