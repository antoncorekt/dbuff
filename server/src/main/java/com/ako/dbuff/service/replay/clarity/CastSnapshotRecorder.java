package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CastSnapshotFacts;
import com.ako.dbuff.service.replay.HeroNames;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.Vector;

/**
 * For each cast of a catalogued skill, records all heroes at the cast tick and then every few ticks
 * for a few seconds. The combat log gives neither the cast position nor the facing, so they come
 * from the caster's entity.
 */
final class CastSnapshotRecorder {
  static final int STEP_TICKS = 6;
  static final int SPAN_TICKS = 150;
  private static final int TICKS_PER_SECOND = 30;
  private static final String HERO_CLASS_PREFIX = "CDOTA_Unit_Hero_";
  private static final String ROTATION = "CBodyComponent.m_angRotation";
  private static final int YAW = 1;
  private static final int FULL_TURN = 360;

  private final Supplier<List<Controllers.View>> players;
  private final Function<Entity, String> heroNames;
  private final Set<String> abilities;
  private final List<Track> active = new ArrayList<>();
  private final List<Frame> frames = new ArrayList<>();

  CastSnapshotRecorder(
      Supplier<List<Controllers.View>> players,
      Function<Entity, String> heroNames,
      Set<String> abilities) {
    this.players = players;
    this.heroNames = heroNames;
    this.abilities = abilities;
  }

  /** A cast seen in the combat log at {@code tick} (game ticks); starts a track and its frame. */
  void cast(String inflictor, String attackerName, double timestamp, long tick) {
    if (!abilities.contains(inflictor)) {
      return;
    }
    List<Controllers.View> views = players.get();
    Integer caster = casterId(views, attackerName);
    if (caster == null) {
      return;
    }
    Track track = new Track(inflictor, timestamp, caster, tick);
    active.add(track);
    capture(track, views, tick);
    track.next = tick + STEP_TICKS;
  }

  void tick(long tick) {
    if (active.isEmpty()) {
      return;
    }
    List<Controllers.View> views = null;
    for (Iterator<Track> it = active.iterator(); it.hasNext(); ) {
      Track track = it.next();
      if (tick > track.start + SPAN_TICKS) {
        it.remove();
      } else if (tick >= track.next) {
        if (views == null) {
          views = players.get();
        }
        capture(track, views, tick);
        track.next = tick + STEP_TICKS;
      }
    }
  }

  /** The snapshots with game seconds counted from 0:00. */
  CastSnapshotFacts result(long clockStartTick, double gameStartTime) {
    List<CastSnapshot> out = new ArrayList<>(frames.size());
    for (Frame f : frames) {
      out.add(
          new CastSnapshot(
              f.ability,
              f.timestamp - gameStartTime,
              (f.tick - clockStartTick) / (double) TICKS_PER_SECOND,
              f.casterId,
              f.facing,
              f.heroes));
    }
    return new CastSnapshotFacts(out);
  }

  private void capture(Track track, List<Controllers.View> views, long tick) {
    List<CastSnapshot.HeroPoint> heroes = new ArrayList<>(views.size());
    int facing = CastSnapshot.NO_FACING;
    for (Controllers.View view : views) {
      Entity hero = view.hero();
      if (hero == null || !Positions.has(hero)) {
        continue;
      }
      heroes.add(
          new CastSnapshot.HeroPoint(
              view.playerId(), Positions.x(hero), Positions.y(hero), alive(hero)));
      if (view.playerId() == track.casterId) {
        facing = facing(hero);
      }
    }
    frames.add(new Frame(track.ability, track.timestamp, tick, track.casterId, facing, heroes));
  }

  private Integer casterId(List<Controllers.View> views, String attackerName) {
    for (Controllers.View view : views) {
      if (view.hero() != null && HeroNames.matches(heroNames.apply(view.hero()), attackerName)) {
        return view.playerId();
      }
    }
    return null;
  }

  static String heroName(Entity hero) {
    String dt = hero.getDtClass().getDtName();
    return dt.startsWith(HERO_CLASS_PREFIX) ? dt.substring(HERO_CLASS_PREFIX.length()) : dt;
  }

  private static boolean alive(Entity hero) {
    return hero.hasProperty("m_lifeState")
        && hero.getProperty("m_lifeState") instanceof Number n
        && n.intValue() == 0;
  }

  private static int facing(Entity hero) {
    if (!hero.hasProperty(ROTATION)) {
      return CastSnapshot.NO_FACING;
    }
    Object value = hero.getProperty(ROTATION);
    double yaw;
    if (value instanceof Vector v && v.getDimension() > YAW) {
      yaw = v.getElement(YAW);
    } else if (value instanceof float[] a && a.length > YAW) {
      yaw = a[YAW];
    } else {
      return CastSnapshot.NO_FACING;
    }
    return Math.floorMod((int) Math.round(yaw), FULL_TURN);
  }

  private static final class Track {
    final String ability;
    final double timestamp;
    final int casterId;
    final long start;
    long next;

    Track(String ability, double timestamp, int casterId, long start) {
      this.ability = ability;
      this.timestamp = timestamp;
      this.casterId = casterId;
      this.start = start;
    }
  }

  private record Frame(
      String ability,
      double timestamp,
      long tick,
      int casterId,
      int facing,
      List<CastSnapshot.HeroPoint> heroes) {}
}
