package com.ako.dbuff.service.replay.analysis.lane;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** A hero of the side stacked jungle camps while laning (OpenDota's per minute count). */
@Component
class StackContributor implements LaneReasonContributor {
  static final String CODE = "STACKED_CAMPS";
  private static final double SEVERITY_PER_STACK = 0.15;
  private static final double SEVERITY_CAP = 0.5;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneReason> reasons = new ArrayList<>();
    for (LaneHero hero : c.ownHeroes()) {
      HeroLaneStats stats = c.facts().stats().get(hero.slot());
      if (stats != null && stats.stacks() > 0) {
        reasons.add(
            LaneReason.helped(
                CODE,
                hero.slot(),
                Math.min(SEVERITY_CAP, SEVERITY_PER_STACK * stats.stacks()),
                "%s stacked %d camp(s) while laning"
                    .formatted(c.name(hero.slot()), stats.stacks())));
      }
    }
    return reasons;
  }
}
