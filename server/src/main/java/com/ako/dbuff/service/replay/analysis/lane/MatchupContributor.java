package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What the heroes' kits say about the lane before it is played: a ranged hero against melee ones,
 * and more nukers and disablers (OpenDota's hero roles) than the other side. Weak by nature, so its
 * severity stays low.
 */
@Component
class MatchupContributor implements LaneReasonContributor {
  static final String CODE = "MATCHUP";
  private static final Set<String> HARASS_ROLES = Set.of("Nuker", "Disabler");
  private static final double RANGED_POINT = 0.5;
  private static final double ROLE_POINT = 0.25;
  private static final double SEVERITY_BASE = 0.1;
  private static final double SEVERITY_STEP = 0.1;
  private static final double SEVERITY_CAP = 0.35;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    if (c.enemyHeroes().isEmpty()) {
      return List.of();
    }
    double difference = points(c, c.ownHeroes()) - points(c, c.enemyHeroes());
    if (Math.abs(difference) < RANGED_POINT) {
      return List.of();
    }
    double severity = Math.min(SEVERITY_CAP, SEVERITY_BASE + SEVERITY_STEP * Math.abs(difference));
    String evidence =
        "by their kits (ranged heroes, nukers and disablers) the heroes of %s were %s than those of %s"
            .formatted(
                c.teamName(), difference > 0 ? "better suited" : "worse suited", c.enemyName());
    return List.of(
        difference > 0
            ? LaneReason.helped(CODE, null, severity, evidence)
            : LaneReason.hurt(CODE, null, severity, evidence));
  }

  private static double points(LaneContext c, List<LaneHero> heroes) {
    double points = 0;
    for (LaneHero hero : heroes) {
      HeroConstant kit = c.facts().kits().get(hero.slot());
      if (kit == null) {
        continue;
      }
      if ("Ranged".equals(kit.getAttack_type())) {
        points += RANGED_POINT;
      }
      if (kit.getRoles() != null) {
        points += ROLE_POINT * kit.getRoles().stream().filter(HARASS_ROLES::contains).count();
      }
    }
    return points;
  }
}
