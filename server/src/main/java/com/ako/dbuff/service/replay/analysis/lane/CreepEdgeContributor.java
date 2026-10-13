package com.ako.dbuff.service.replay.analysis.lane;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** More siege creep last hits, or more denies, than the other side: the experience it took. */
@Component
class CreepEdgeContributor implements LaneReasonContributor {
  static final String SIEGE = "SIEGE_EDGE";
  static final String DENIES = "DENY_EDGE";
  private static final double SIEGE_BASE = 0.2;
  private static final double SIEGE_STEP = 0.1;
  private static final double DENY_BASE = 0.15;
  private static final double DENY_STEP = 0.04;
  private static final double CAP = 0.6;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneReason> reasons = new ArrayList<>();
    LaneProperties p = c.facts().properties();
    int siege = c.own().siegeLastHits() - c.enemy().siegeLastHits();
    if (Math.abs(siege) >= p.getSiegeEdge()) {
      reasons.add(
          edge(
              SIEGE,
              siege,
              Math.min(CAP, SIEGE_BASE + SIEGE_STEP * Math.abs(siege)),
              "%s had %d siege creep last hits against %d"
                  .formatted(c.teamName(), c.own().siegeLastHits(), c.enemy().siegeLastHits())));
    }
    int denies = c.own().denies() - c.enemy().denies();
    if (Math.abs(denies) >= p.getDenyEdge()) {
      reasons.add(
          edge(
              DENIES,
              denies,
              Math.min(CAP, DENY_BASE + DENY_STEP * Math.abs(denies)),
              "%s had %d denies against %d"
                  .formatted(c.teamName(), c.own().denies(), c.enemy().denies())));
    }
    return reasons;
  }

  private static LaneReason edge(String code, int difference, double severity, String evidence) {
    return difference > 0
        ? LaneReason.helped(code, null, severity, evidence)
        : LaneReason.hurt(code, null, severity, evidence);
  }
}
