package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.service.replay.map.Zone;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The kind of creep a unit name stands for. This is the one place that reads creep names: lane
 * creeps are {@code npc_dota_creep_<side>_<kind>[_upgraded][_mega]} and the siege creep is {@code
 * npc_dota_<side>_siege}, without the {@code creep_} part; camp creeps are {@code
 * npc_dota_neutral_*}. Summons of heroes (wolves, skeletons, zombies), wards, couriers, Roshan and
 * buildings are no creep class.
 */
public enum CreepClass {
  LANE_MELEE,
  LANE_RANGED,
  LANE_SIEGE,
  LANE_FLAGBEARER,
  LANE_MELEE_UPGRADED,
  LANE_RANGED_UPGRADED,
  LANE_SIEGE_UPGRADED,
  LANE_FLAGBEARER_UPGRADED,
  LANE_MEGA,
  /** A lane creep whose name has no known kind. */
  LANE_OTHER,
  NEUTRAL,
  NEUTRAL_ANCIENT;

  private static final String NEUTRAL_PREFIX = "npc_dota_neutral_";
  private static final Pattern LANE_CREEP =
      Pattern.compile("npc_dota_creep_(?:goodguys|badguys)_(\\w+)");
  private static final Pattern SIEGE = Pattern.compile("npc_dota_(?:goodguys|badguys)_(siege\\w*)");
  private static final Set<String> ANCIENT_UNITS =
      Set.of(
          "black_dragon",
          "black_drake",
          "granite_golem",
          "rock_golem",
          "ice_shaman",
          "frostbitten_golem",
          "big_thunder_lizard",
          "small_thunder_lizard",
          "prowler_shaman",
          "prowler_acolyte",
          "ancient_frog",
          "ancient_frog_mage");

  public boolean isLane() {
    return name().startsWith("LANE_");
  }

  public boolean isFlagbearer() {
    return this == LANE_FLAGBEARER || this == LANE_FLAGBEARER_UPGRADED;
  }

  public boolean isNeutral() {
    return !isLane();
  }

  /** The class of a unit name, or null when it is no lane or camp creep. */
  public static CreepClass of(String unit) {
    if (unit == null) {
      return null;
    }
    if (unit.startsWith(NEUTRAL_PREFIX)) {
      return ANCIENT_UNITS.contains(unit.substring(NEUTRAL_PREFIX.length()))
          ? NEUTRAL_ANCIENT
          : NEUTRAL;
    }
    var lane = LANE_CREEP.matcher(unit);
    if (!lane.matches()) {
      lane = SIEGE.matcher(unit);
      if (!lane.matches()) {
        return null;
      }
    }
    String kind = lane.group(1);
    if (kind.endsWith("_mega")) {
      return LANE_MEGA;
    }
    boolean upgraded = kind.contains("_upgraded");
    if (kind.startsWith("melee")) {
      return upgraded ? LANE_MELEE_UPGRADED : LANE_MELEE;
    }
    if (kind.startsWith("ranged")) {
      return upgraded ? LANE_RANGED_UPGRADED : LANE_RANGED;
    }
    if (kind.startsWith("siege")) {
      return upgraded ? LANE_SIEGE_UPGRADED : LANE_SIEGE;
    }
    if (kind.startsWith("flagbearer")) {
      return upgraded ? LANE_FLAGBEARER_UPGRADED : LANE_FLAGBEARER;
    }
    return LANE_OTHER;
  }

  /** The half of the map a creep fights for, or null for a camp creep or a name without a side. */
  public static Zone.Half sideOf(String unit) {
    if (unit.contains("goodguys")) {
      return Zone.Half.RADIANT;
    }
    return unit.contains("badguys") ? Zone.Half.DIRE : null;
  }
}
