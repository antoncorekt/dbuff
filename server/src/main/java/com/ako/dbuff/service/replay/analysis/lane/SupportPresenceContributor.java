package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A support (role 4 or 5) in the lane for one side and none for the other. Not in mid, and not when
 * the side holding the support has a single hero in the lane.
 */
@Component
class SupportPresenceContributor implements LaneReasonContributor {
  static final String CODE = "SUPPORT_PRESENCE";
  private static final double SEVERITY = 0.35;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneHero> own = supports(c, c.ownHeroes());
    List<LaneHero> enemy = supports(c, c.enemyHeroes());
    if (c.pair().lane() == Zone.Lane.MID
        || own.isEmpty() == enemy.isEmpty()
        || c.enemyHeroes().isEmpty()
        || soloSupport(c, own)) {
      return List.of();
    }
    return List.of(
        own.isEmpty()
            ? LaneReason.hurt(
                CODE,
                null,
                SEVERITY,
                "%s had no support in the lane, %s had %s"
                    .formatted(c.teamName(), c.enemyName(), names(c, enemy)))
            : LaneReason.helped(
                CODE,
                own.get(0).slot(),
                SEVERITY,
                "%s had %s in the lane, %s had no support"
                    .formatted(c.teamName(), names(c, own), c.enemyName())));
  }

  private static boolean soloSupport(LaneContext c, List<LaneHero> own) {
    return own.isEmpty() ? c.enemyHeroes().size() == 1 : c.ownHeroes().size() == 1;
  }

  private static List<LaneHero> supports(LaneContext c, List<LaneHero> heroes) {
    return heroes.stream().filter(h -> c.facts().isSupport(h.slot())).toList();
  }

  private static String names(LaneContext c, List<LaneHero> heroes) {
    return LaneContext.list(heroes.stream().map(h -> c.name(h.slot())).toList());
  }
}
