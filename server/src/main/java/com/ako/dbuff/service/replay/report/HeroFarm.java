package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import java.util.List;
import java.util.OptionalInt;

/**
 * The gold one hero got from lane creeps and from camps in each minute of the game, from its stored
 * creep kills. Denies, rows of an unknown creep class and rows without a usable time are left out.
 */
record HeroFarm(long slot, double[] lane, double[] neutral) {
  static final int MAX_MINUTES = 300;
  private static final double SECONDS_PER_MINUTE = 60;
  private static final int WINDOW_MINUTES = 3;
  private static final double MOSTLY = 0.6;

  static HeroFarm of(long slot, List<CreepKillDomain> kills) {
    int minutes = 0;
    for (CreepKillDomain k : kills) {
      minutes = Math.max(minutes, minute(k) + 1);
    }
    double[] lane = new double[minutes];
    double[] neutral = new double[minutes];
    for (CreepKillDomain k : kills) {
      CreepClass kind = kind(k);
      if (kind == null) {
        continue;
      }
      double gold =
          (k.getGold() == null ? 0 : k.getGold())
              + (k.getBonusGold() == null ? 0 : k.getBonusGold());
      (kind.isNeutral() ? neutral : lane)[minute(k)] += gold;
    }
    return new HeroFarm(slot, lane, neutral);
  }

  /** The creep class of a row that counts as farm, else null. */
  private static CreepClass kind(CreepKillDomain k) {
    if (Boolean.TRUE.equals(k.getDenied()) || minute(k) < 0) {
      return null;
    }
    try {
      CreepClass kind = CreepClass.valueOf(k.getCreepClass());
      return kind.isLane() || kind.isNeutral() ? kind : null;
    } catch (IllegalArgumentException | NullPointerException e) {
      return null;
    }
  }

  /** The minute of the kill, -1 when it has no usable time. */
  private static int minute(CreepKillDomain k) {
    Double seconds = k.getSeconds();
    if (seconds == null || seconds.isNaN() || seconds.isInfinite()) {
      return -1;
    }
    int minute = (int) Math.floor(seconds / SECONDS_PER_MINUTE);
    return minute >= MAX_MINUTES ? -1 : Math.max(0, minute);
  }

  double laneGold() {
    return sum(lane, 0);
  }

  double neutralGold() {
    return sum(neutral, 0);
  }

  double totalGold() {
    return laneGold() + neutralGold();
  }

  /** Whether most of the farm gold came from camps, from lanes, or from neither clearly. */
  Mix mix() {
    double share = neutralGold() / totalGold();
    return share >= MOSTLY ? Mix.JUNGLE : share <= 1 - MOSTLY ? Mix.LANE : Mix.SPLIT;
  }

  enum Mix {
    JUNGLE,
    LANE,
    SPLIT
  }

  /**
   * The game second from which the hero lived in the jungle: the first minute from which camps
   * outearned lanes both over the next few minutes and over the rest of the game, moved back to the
   * start of the jungle stretch that was under way then.
   */
  OptionalInt jungleFrom(List<PlayerActivitySegmentDomain> segments) {
    for (int m = 0; m < neutral.length; m++) {
      if (sum(neutral, m, m + WINDOW_MINUTES) > sum(lane, m, m + WINDOW_MINUTES)
          && sum(neutral, m) > sum(lane, m)) {
        int minuteStart = (int) (m * SECONDS_PER_MINUTE);
        int earliest = (int) (minuteStart - SECONDS_PER_MINUTE);
        int from =
            segments.stream()
                .filter(s -> Activity.JUNGLE_FARM.name().equals(s.getActivity()))
                .filter(s -> s.getStartSeconds() != null && s.getEndSeconds() != null)
                .filter(s -> s.getEndSeconds() > earliest)
                .mapToInt(PlayerActivitySegmentDomain::getStartSeconds)
                .min()
                .orElse(minuteStart);
        return OptionalInt.of(Math.max(from, Math.max(0, earliest)));
      }
    }
    return OptionalInt.empty();
  }

  private static double sum(double[] values, int from) {
    return sum(values, from, values.length);
  }

  private static double sum(double[] values, int from, int to) {
    double total = 0;
    for (int i = from; i < Math.min(to, values.length); i++) {
      total += values[i];
    }
    return total;
  }
}
