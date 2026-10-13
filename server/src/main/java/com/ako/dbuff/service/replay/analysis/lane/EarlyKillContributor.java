package com.ako.dbuff.service.replay.analysis.lane;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** A hero of the lane died, or was killed by the lane's other side, before the early limit. */
@Component
class EarlyKillContributor implements LaneReasonContributor {
  static final String DEATH = "EARLY_DEATH";
  static final String KILL = "EARLY_KILL";
  private static final double SEVERITY_FLOOR = 0.3;
  private static final double SEVERITY_RANGE = 0.3;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneReason> reasons = new ArrayList<>();
    int limit = c.facts().properties().getEarlyKillSeconds();
    for (LaneDeath death : c.facts().deaths()) {
      if (death.seconds() >= limit || death.seconds() < 0) {
        continue;
      }
      double severity = SEVERITY_FLOOR + SEVERITY_RANGE * (1 - death.seconds() / limit);
      boolean ownDied = isLaning(c, c.ownHeroes(), death.victimSlot(), death.seconds());
      if (ownDied) {
        reasons.add(
            LaneReason.hurt(
                DEATH,
                death.victimSlot(),
                severity,
                "%s died at %s"
                    .formatted(c.name(death.victimSlot()), LaneContext.clock(death.seconds()))));
      } else if (death.killerSlot() != null
          && isLaning(c, c.enemyHeroes(), death.victimSlot(), death.seconds())
          && isLaning(c, c.ownHeroes(), death.killerSlot(), death.seconds())) {
        reasons.add(
            LaneReason.helped(
                KILL,
                death.killerSlot(),
                severity,
                "%s killed %s at %s"
                    .formatted(
                        c.name(death.killerSlot()),
                        c.name(death.victimSlot()),
                        LaneContext.clock(death.seconds()))));
      }
    }
    return reasons;
  }

  private static boolean isLaning(LaneContext c, List<LaneHero> heroes, long slot, double second) {
    return heroes.stream().anyMatch(h -> h.slot() == slot && h.window().contains(second));
  }
}
