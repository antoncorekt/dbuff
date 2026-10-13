package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.lane.MinuteSeries;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;

/**
 * What the reasons of a rotation read besides the trip: the totals of gold and experience each
 * minute by slot, the hits the heroes dealt, and when each team lost a tower.
 */
public record MidGameFacts(
    Map<Long, MinuteSeries> gold,
    Map<Long, MinuteSeries> xp,
    List<DamageHit> hits,
    Map<TeamSide, List<Double>> towerLosses) {
  public static final MidGameFacts NONE = new MidGameFacts(Map.of(), Map.of(), List.of(), Map.of());
}
