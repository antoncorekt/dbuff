package com.ako.dbuff.service.replay.map;

import com.ako.dbuff.service.replay.MapFacts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tells where a world coordinate is: in a base, on a lane, in the river, or in the jungle (and
 * which camp). Built from the buildings of the replay itself, so a patch that moves a tower moves
 * the lanes with it instead of leaving hard-coded numbers behind.
 */
public final class MapLayout {
  private static final String RADIANT_FORT = "dota_goodguys_fort";
  private static final String DIRE_FORT = "dota_badguys_fort";

  /** Distances in world units; see {@link #MapLayout(MapFacts, Tuning)}. */
  public record Tuning(double baseRadius, double laneHalfWidth, double riverHalfWidth) {
    public static Tuning defaults() {
      return new Tuning(2400, 900, 700);
    }
  }

  private final MapFacts facts;
  private final Tuning tuning;
  private final Map<Zone.Lane, List<double[]>> lanes = new EnumMap<>(Zone.Lane.class);
  private final double[] radiantFort;
  private final double[] direFort;
  private final double[] middle;
  private final double[] axis;

  public MapLayout(MapFacts facts, Tuning tuning) {
    this.facts = facts;
    this.tuning = tuning;
    this.radiantFort = position(facts, RADIANT_FORT);
    this.direFort = position(facts, DIRE_FORT);
    this.middle =
        new double[] {(radiantFort[0] + direFort[0]) / 2, (radiantFort[1] + direFort[1]) / 2};
    double length = Math.hypot(direFort[0] - radiantFort[0], direFort[1] - radiantFort[1]);
    this.axis =
        new double[] {
          (direFort[0] - radiantFort[0]) / length, (direFort[1] - radiantFort[1]) / length
        };
    for (Zone.Lane lane : Zone.Lane.values()) {
      lanes.put(lane, laneRoute(facts, lane));
    }
  }

  /** The buildings and camps this layout was built from. */
  public MapFacts facts() {
    return facts;
  }

  public String version() {
    return facts.version();
  }

  public Zone zone(double x, double y) {
    Zone.Half half = half(x, y);
    if (Math.hypot(x - radiantFort[0], y - radiantFort[1]) <= tuning.baseRadius()) {
      return new Zone(Zone.Kind.BASE, null, Zone.Half.RADIANT, Zone.NO_CAMP);
    }
    if (Math.hypot(x - direFort[0], y - direFort[1]) <= tuning.baseRadius()) {
      return new Zone(Zone.Kind.BASE, null, Zone.Half.DIRE, Zone.NO_CAMP);
    }
    int camp = campAt(x, y);
    if (camp >= 0) {
      return new Zone(Zone.Kind.JUNGLE, null, half, camp);
    }
    Zone.Lane nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Map.Entry<Zone.Lane, List<double[]>> lane : lanes.entrySet()) {
      double distance = distanceToRoute(lane.getValue(), x, y);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = lane.getKey();
      }
    }
    if (nearestDistance <= tuning.laneHalfWidth()) {
      return new Zone(Zone.Kind.LANE, nearest, half, Zone.NO_CAMP);
    }
    if (Math.abs(side(x, y)) <= tuning.riverHalfWidth()) {
      return new Zone(Zone.Kind.RIVER, null, half, Zone.NO_CAMP);
    }
    return new Zone(Zone.Kind.JUNGLE, null, half, Zone.NO_CAMP);
  }

  public int campCount() {
    return facts.camps().size();
  }

  private Zone.Half half(double x, double y) {
    return side(x, y) < 0 ? Zone.Half.RADIANT : Zone.Half.DIRE;
  }

  /** Signed distance from the river's centre line, negative on the Radiant side. */
  private double side(double x, double y) {
    return (x - middle[0]) * axis[0] + (y - middle[1]) * axis[1];
  }

  private int campAt(double x, double y) {
    List<MapFacts.Camp> camps = facts.camps();
    for (int i = 0; i < camps.size(); i++) {
      MapFacts.Camp c = camps.get(i);
      if (x >= c.minX() && x <= c.maxX() && y >= c.minY() && y <= c.maxY()) {
        return i;
      }
    }
    return Zone.NO_CAMP;
  }

  /**
   * Where a building stands, by the name the combat log or the map uses; empty if unknown. The
   * combat log's barracks and fourth towers are named differently from the map's, and its fourth
   * tower has no lane, so one of the two, which stand side by side, answers for it.
   */
  public Optional<double[]> buildingPosition(String name) {
    String key = name.startsWith("npc_") ? name.substring("npc_".length()) : name;
    key = key.replaceAll("^dota_(good|bad)guys_(melee|range)_rax_(top|mid|bot)$", "$1_rax_$2_$3");
    for (String candidate :
        key.endsWith("_tower4") ? List.of(key + "_bot", key + "_top") : List.of(key)) {
      Optional<double[]> found =
          facts.buildings().stream()
              .filter(b -> b.name().equals(candidate))
              .findFirst()
              .map(b -> new double[] {b.x(), b.y()});
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** Radiant's third, second and first tower, then Dire's first, second and third. */
  private static List<double[]> laneRoute(MapFacts facts, Zone.Lane lane) {
    String suffix =
        switch (lane) {
          case TOP -> "top";
          case MID -> "mid";
          case BOTTOM -> "bot";
        };
    List<double[]> route = new ArrayList<>();
    for (int tier = 3; tier >= 1; tier--) {
      route.add(position(facts, "dota_goodguys_tower" + tier + "_" + suffix));
    }
    for (int tier = 1; tier <= 3; tier++) {
      route.add(position(facts, "dota_badguys_tower" + tier + "_" + suffix));
    }
    return route;
  }

  private static double[] position(MapFacts facts, String name) {
    return facts.buildings().stream()
        .filter(b -> b.name().equals(name))
        .findFirst()
        .map(b -> new double[] {b.x(), b.y()})
        .orElseThrow(() -> new IllegalArgumentException("The map has no building " + name));
  }

  private static double distanceToRoute(List<double[]> route, double x, double y) {
    double best = Double.MAX_VALUE;
    for (int i = 1; i < route.size(); i++) {
      best = Math.min(best, distanceToSegment(route.get(i - 1), route.get(i), x, y));
    }
    return best;
  }

  private static double distanceToSegment(double[] a, double[] b, double x, double y) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double lengthSquared = dx * dx + dy * dy;
    double t = lengthSquared == 0 ? 0 : ((x - a[0]) * dx + (y - a[1]) * dy) / lengthSquared;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(x - (a[0] + t * dx), y - (a[1] + t * dy));
  }
}
