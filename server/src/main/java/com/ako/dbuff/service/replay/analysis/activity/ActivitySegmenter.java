package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns one hero's sampled positions into stretches of activity. A sample counts as farming when
 * the hero last-hit a creep close to it in time, because position alone cannot tell a hero farming
 * a lane from one standing in it.
 */
public final class ActivitySegmenter {
  private static final String TELEPORTING = "modifier_teleporting";

  private final ActivityTuning tuning;

  public ActivitySegmenter(ActivityTuning tuning) {
    this.tuning = tuning;
  }

  /**
   * One hero's samples, each array as long as the number of samples.
   *
   * @param ownHalf the half of the map the hero's team starts in
   * @param level zero where there is no data
   * @param flags bit 0 set while alive
   */
  public record Track(
      Zone.Half ownHalf,
      int startSeconds,
      int stepSeconds,
      int[] x,
      int[] y,
      int[] level,
      int[] flags) {}

  public List<ActivitySegment> segment(
      Track track, String heroName, List<CombatEvent> events, MapLayout layout) {
    List<FarmHit> farm = farmHits(heroName, track.ownHalf(), events);
    List<TeleportWindow> teleports = teleportWindows(heroName, events);
    List<ActivitySegment> segments = new ArrayList<>();
    ActivitySegment open = null;
    for (int i = 0; i < track.x().length; i++) {
      if (track.level()[i] == 0) {
        open = close(segments, open);
        continue;
      }
      int time = track.startSeconds() + i * track.stepSeconds();
      ActivitySegment sample = classify(track, i, time, farm, teleports, layout);
      if (open != null && sameKind(open, sample)) {
        open =
            new ActivitySegment(
                open.startSeconds(),
                sample.endSeconds(),
                open.activity(),
                open.zone(),
                open.lane(),
                open.side());
      } else {
        close(segments, open);
        open = sample;
      }
    }
    close(segments, open);
    return segments;
  }

  private ActivitySegment classify(
      Track track,
      int i,
      int time,
      List<FarmHit> farm,
      List<TeleportWindow> teleports,
      MapLayout layout) {
    Zone zone = layout.zone(track.x()[i], track.y()[i]);
    Zone.Lane lane = zone.kind() == Zone.Kind.LANE ? zone.lane() : null;
    ActivitySegment.Side side = side(zone, track.ownHalf());
    Activity activity = activity(track, i, time, zone, side, farm, teleports);
    return new ActivitySegment(time, time + track.stepSeconds(), activity, zone.kind(), lane, side);
  }

  private Activity activity(
      Track track,
      int i,
      int time,
      Zone zone,
      ActivitySegment.Side side,
      List<FarmHit> farm,
      List<TeleportWindow> teleports) {
    if ((track.flags()[i] & 1) == 0) {
      return Activity.DEAD;
    }
    if (teleports.stream().anyMatch(w -> time >= w.start() && time <= w.end())) {
      return Activity.TELEPORTING;
    }
    int neutral = 0;
    int lane = 0;
    for (FarmHit hit : farm) {
      if (hit.second() >= time - tuning.farmLookbackSeconds()
          && hit.second() < time + track.stepSeconds()) {
        if (hit.neutral()) {
          neutral++;
        } else {
          lane++;
        }
      }
    }
    if (neutral > 0 && neutral >= lane) {
      return Activity.JUNGLE_FARM;
    }
    if (lane > 0) {
      return Activity.LANE_FARM;
    }
    if (zone.kind() == Zone.Kind.BASE) {
      return side == ActivitySegment.Side.OWN ? Activity.IN_BASE : Activity.PUSHING;
    }
    if (zone.kind() == Zone.Kind.LANE && side == ActivitySegment.Side.ENEMY) {
      return Activity.PUSHING;
    }
    if (i > 0 && track.level()[i - 1] > 0 && onFoot(speed(track, i))) {
      return Activity.TRAVELING;
    }
    return Activity.POSITIONED;
  }

  /** Fast enough to be walking somewhere, not so fast that it was a teleport or a respawn. */
  private boolean onFoot(double speed) {
    return speed >= tuning.travelUnitsPerSecond() && speed <= tuning.jumpUnitsPerSecond();
  }

  private static double speed(Track track, int i) {
    double distance = Math.hypot(track.x()[i] - track.x()[i - 1], track.y()[i] - track.y()[i - 1]);
    return distance / track.stepSeconds();
  }

  private static ActivitySegment.Side side(Zone zone, Zone.Half ownHalf) {
    if (zone.kind() == Zone.Kind.RIVER) {
      return null;
    }
    return zone.half() == ownHalf ? ActivitySegment.Side.OWN : ActivitySegment.Side.ENEMY;
  }

  private record FarmHit(double second, boolean neutral) {}

  private record TeleportWindow(double start, double end) {}

  /** Last hits on neutrals and on the enemy's lane creeps; an own creep is a deny, not farm. */
  private static List<FarmHit> farmHits(String hero, Zone.Half ownHalf, List<CombatEvent> events) {
    List<FarmHit> hits = new ArrayList<>();
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DEATH
          || !e.attackerIsRealHero()
          || !Objects.equals(e.attacker(), hero)
          || (e.flags() & CombatEvent.TARGET_HERO) != 0) {
        continue;
      }
      CreepClass creep = CreepClass.of(e.target());
      if (creep == null) {
        continue;
      }
      if (creep.isNeutral()) {
        hits.add(new FarmHit(e.gameSecond(), true));
      } else if (CreepClass.sideOf(e.target()) != ownHalf) {
        hits.add(new FarmHit(e.gameSecond(), false));
      }
    }
    return hits;
  }

  /** From each teleport modifier added on the hero to the one removed after it. */
  private static List<TeleportWindow> teleportWindows(String hero, List<CombatEvent> events) {
    List<TeleportWindow> windows = new ArrayList<>();
    double start = Double.NaN;
    for (CombatEvent e : events) {
      if (!TELEPORTING.equals(e.inflictor()) || !Objects.equals(e.target(), hero)) {
        continue;
      }
      if (e.kind() == CombatEventKind.MODIFIER_ADD) {
        start = e.gameSecond();
      } else if (e.kind() == CombatEventKind.MODIFIER_REMOVE && !Double.isNaN(start)) {
        windows.add(new TeleportWindow(start, e.gameSecond()));
        start = Double.NaN;
      }
    }
    return windows;
  }

  private static boolean sameKind(ActivitySegment a, ActivitySegment b) {
    return a.activity() == b.activity()
        && a.zone() == b.zone()
        && a.lane() == b.lane()
        && a.side() == b.side()
        && a.endSeconds() == b.startSeconds();
  }

  private static ActivitySegment close(List<ActivitySegment> segments, ActivitySegment open) {
    if (open != null) {
      segments.add(open);
    }
    return null;
  }
}
