package com.ako.dbuff.service.replay.analysis.lane;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * More hero damage taken than dealt against the lane's opponents hurt the side; the other way
 * helped it. Damage from enemies of other lanes is a gank, not harassment.
 */
@Component
class HarassmentContributor implements LaneReasonContributor {
  static final String CODE = "HARASSMENT";
  private static final double SEVERITY_STEP = 0.15;
  private static final double SEVERITY_CAP = 0.7;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    long taken = 0;
    long dealt = 0;
    for (LaneHero own : c.ownHeroes()) {
      for (LaneHero opponent : c.enemyHeroes()) {
        taken += c.facts().damage().takenFrom(own.slot(), opponent.slot());
        dealt += c.facts().damage().takenFrom(opponent.slot(), own.slot());
      }
    }
    long threshold = c.facts().properties().getHarassmentDamage();
    long difference = taken - dealt;
    if (Math.abs(difference) < threshold) {
      return List.of();
    }
    double severity =
        Math.min(SEVERITY_CAP, Math.abs(difference) / (double) threshold * SEVERITY_STEP);
    String evidence =
        "%s dealt %d and took %d hero damage in the lane".formatted(c.teamName(), dealt, taken);
    return List.of(
        difference > 0
            ? LaneReason.hurt(CODE, null, severity, evidence)
            : LaneReason.helped(CODE, null, severity, evidence));
  }
}
