package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Objects;

/**
 * A tower that fell, with its owner, the lane (null for a tower on no lane) and whether its own
 * team denied it.
 */
public record TowerFall(TeamSide owner, double seconds, Zone.Lane lane, boolean denied) {
  private static final String TOWER_KIND = "TOWER";

  /** The towers among the stored building kills that have a time and a known owner. */
  public static List<TowerFall> of(List<BuildingKillContextDomain> buildings) {
    return buildings.stream()
        .filter(b -> TOWER_KIND.equals(b.getKind()) && b.getSeconds() != null)
        .map(TowerFall::of)
        .filter(Objects::nonNull)
        .toList();
  }

  private static TowerFall of(BuildingKillContextDomain b) {
    TeamSide owner = Enums.find(TeamSide.class, b.getOwnerTeam());
    if (owner == null) {
      return null;
    }
    Zone.Lane lane = b.getLane() == null ? null : Enums.find(Zone.Lane.class, b.getLane());
    return new TowerFall(owner, b.getSeconds(), lane, Boolean.TRUE.equals(b.getDenied()));
  }
}
