package com.ako.dbuff.service.replay;

import java.util.List;

/**
 * The heroes at one instant around a catalogued cast. The replay has no property that says which
 * team sees a hero, so no visibility is recorded.
 *
 * @param ability the inflictor of the cast, e.g. {@code pudge_meat_hook}
 * @param castSecond game second of the cast, as in the combat log
 * @param second game second of this frame
 * @param casterId replay player id of the caster
 * @param facing the caster's yaw in degrees, 0 to 359, or {@link #NO_FACING}
 * @param heroes every hero that existed at the frame
 */
public record CastSnapshot(
    String ability,
    double castSecond,
    double second,
    int casterId,
    int facing,
    List<HeroPoint> heroes) {
  public static final int NO_FACING = -1;

  /** One hero: replay player id, world position and whether it was alive. */
  public record HeroPoint(int playerId, int x, int y, boolean alive) {}
}
