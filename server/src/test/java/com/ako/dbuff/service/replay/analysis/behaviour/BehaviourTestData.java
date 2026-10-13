package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

final class BehaviourTestData {
  static final int STEP = 5;
  static final int SAMPLES = 40;

  static final Zone BASE = new Zone(Zone.Kind.BASE, null, Zone.Half.RADIANT, Zone.NO_CAMP);
  static final Zone TOP = new Zone(Zone.Kind.LANE, Zone.Lane.TOP, Zone.Half.DIRE, Zone.NO_CAMP);
  static final Zone MID = new Zone(Zone.Kind.LANE, Zone.Lane.MID, Zone.Half.RADIANT, Zone.NO_CAMP);
  static final Zone BOTTOM =
      new Zone(Zone.Kind.LANE, Zone.Lane.BOTTOM, Zone.Half.RADIANT, Zone.NO_CAMP);

  /** x below -6000 is base, below -2000 the top lane, below 6000 mid, else the bottom lane. */
  static final ZoneLookup ZONES =
      (x, y) -> x < -6000 ? BASE : x < -2000 ? TOP : x < 6000 ? MID : BOTTOM;

  private BehaviourTestData() {}

  /** A hero that stands at {@code x(i)} at sample i, alive unless {@code dead(i)}. */
  static Trace trace(IntUnaryOperator x, IntUnaryOperator dead) {
    int[] xs = new int[SAMPLES];
    int[] flags = new int[SAMPLES];
    int[] level = new int[SAMPLES];
    Arrays.setAll(xs, x::applyAsInt);
    Arrays.setAll(flags, i -> dead.applyAsInt(i) == 1 ? 0 : 1);
    Arrays.fill(level, 6);
    return new Trace(0, STEP, xs, new int[SAMPLES], level, flags);
  }

  /** Like {@link #trace} with {@code samples} samples, one every {@link #STEP} seconds. */
  static Trace trace(int samples, IntUnaryOperator x, IntUnaryOperator dead) {
    int[] xs = new int[samples];
    int[] flags = new int[samples];
    int[] level = new int[samples];
    Arrays.setAll(xs, x::applyAsInt);
    Arrays.setAll(flags, i -> dead.applyAsInt(i) == 1 ? 0 : 1);
    Arrays.fill(level, 6);
    return new Trace(0, STEP, xs, new int[samples], level, flags);
  }

  static Trace stayingAt(int x) {
    return trace(i -> x, i -> 0);
  }
}
