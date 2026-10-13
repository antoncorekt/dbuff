package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.MapFacts;
import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Optional;

/**
 * A lane tower of the map (tier 1 to 3) with its owner, lane and position; whether it still stands
 * is worked out from the towers that fell.
 *
 * @param name the map's name, without the combat log's {@code npc_} prefix
 */
public record StandingTower(String name, TeamSide owner, Zone.Lane lane, Point position) {
  private static final String PREFIX = "npc_";
  private static final int LAST_LANE_TIER = 3;

  /** The lane towers of the map. */
  static List<StandingTower> of(MapFacts facts) {
    return facts.buildings().stream()
        .map(b -> tower(b, BuildingName.parse(b.name())))
        .flatMap(Optional::stream)
        .toList();
  }

  /** The name a fallen building is compared by, the same for the map and the combat log. */
  static String key(String name) {
    return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
  }

  private static Optional<StandingTower> tower(MapFacts.Building b, Optional<BuildingName> name) {
    if (name.isEmpty()
        || !"TOWER".equals(name.get().kind())
        || name.get().tier() > LAST_LANE_TIER
        || name.get().lane() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new StandingTower(
            key(b.name()),
            Enums.find(TeamSide.class, name.get().team()),
            Enums.find(Zone.Lane.class, name.get().lane()),
            new Point(b.x(), b.y())));
  }
}
