package com.ako.dbuff.service.replay.analysis.activity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
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
  private static final String NEUTRAL = "npc_dota_neutral_";
  private static final String LANE_CREEP = "npc_dota_creep_";

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
    List<double[]> farm = farmHits(heroName, events);
    List<double[]> teleports = teleportWindows(heroName, events);
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
      List<double[]> farm,
      List<double[]> teleports,
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
      List<double[]> farm,
      List<double[]> teleports) {
    if ((track.flags()[i] & 1) == 0) {
      return Activity.DEAD;
    }
    if (teleports.stream().anyMatch(w -> time >= w[0] && time <= w[1])) {
      return Activity.TELEPORTING;
    }
    int neutral = 0;
    int lane = 0;
    for (double[] hit : farm) {
      if (Math.abs(hit[0] - time) <= tuning.farmWindowSeconds()) {
        if (hit[1] > 0) {
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
    if (i > 0 && track.level()[i - 1] > 0 && speed(track, i) >= tuning.travelUnitsPerSecond()) {
      return Activity.TRAVELING;
    }
    return Activity.POSITIONED;
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

  /** Creep last hits by the hero: second, and 1 for a neutral, 0 for a lane creep. */
  private static List<double[]> farmHits(String hero, List<CombatEvent> events) {
    List<double[]> hits = new ArrayList<>();
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DEATH
          || !e.attackerIsRealHero()
          || !Objects.equals(e.attacker(), hero)
          || (e.flags() & CombatEvent.TARGET_HERO) != 0) {
        continue;
      }
      if (e.target().startsWith(NEUTRAL)) {
        hits.add(new double[] {e.gameSecond(), 1});
      } else if (e.target().startsWith(LANE_CREEP)) {
        hits.add(new double[] {e.gameSecond(), 0});
      }
    }
    return hits;
  }

  /** From each teleport modifier added on the hero to the one removed after it. */
  private static List<double[]> teleportWindows(String hero, List<CombatEvent> events) {
    List<double[]> windows = new ArrayList<>();
    double start = Double.NaN;
    for (CombatEvent e : events) {
      if (!TELEPORTING.equals(e.inflictor()) || !Objects.equals(e.target(), hero)) {
        continue;
      }
      if (e.kind() == CombatEventKind.MODIFIER_ADD) {
        start = e.gameSecond();
      } else if (e.kind() == CombatEventKind.MODIFIER_REMOVE && !Double.isNaN(start)) {
        windows.add(new double[] {start, e.gameSecond()});
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
