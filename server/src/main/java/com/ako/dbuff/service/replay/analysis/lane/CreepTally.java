package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import java.util.List;

/**
 * Counts and values the lane creeps a hero killed inside a window. A last hit is worth its gold
 * plus its experience times {@code xpWeight}, so a siege creep outweighs a melee creep by its own
 * values; a deny is worth the experience it takes from the enemy (and a little of the gold).
 */
final class CreepTally {
  private final CreepValues values;
  private final LaneProperties properties;

  CreepTally(CreepValues values, LaneProperties properties) {
    this.values = values;
    this.properties = properties;
  }

  /** The creep part of the stats; the other fields of the result are filled by the caller. */
  Result tally(List<CreepKillDomain> kills, LaningWindow window) {
    int lastHits = 0;
    int melee = 0;
    int ranged = 0;
    int siege = 0;
    int denies = 0;
    double score = 0;
    for (CreepKillDomain k : kills) {
      CreepClass creep = k.getCreepClass() == null ? null : CreepClass.valueOf(k.getCreepClass());
      if (creep == null
          || !creep.isLane()
          || k.getSeconds() == null
          || !window.contains(k.getSeconds())) {
        continue;
      }
      var value = values.of(creep);
      double gold =
          value.map(v -> v.gold() + (v.bonusGold() == null ? 0 : v.bonusGold())).orElse(0);
      double xp = value.map(v -> v.xp() == null ? 0 : v.xp()).orElse(0);
      if (Boolean.TRUE.equals(k.getDenied())) {
        denies++;
        score += properties.getDenyXpWeight() * xp + properties.getDenyGoldWeight() * gold;
        continue;
      }
      lastHits++;
      score += gold + properties.getXpWeight() * xp;
      switch (creep) {
        case LANE_MELEE, LANE_MELEE_UPGRADED -> melee++;
        case LANE_RANGED, LANE_RANGED_UPGRADED -> ranged++;
        case LANE_SIEGE, LANE_SIEGE_UPGRADED -> siege++;
        default -> {}
      }
    }
    return new Result(lastHits, melee, ranged, siege, denies, score);
  }

  record Result(
      int lastHits,
      int meleeLastHits,
      int rangedLastHits,
      int siegeLastHits,
      int denies,
      double score) {}
}
