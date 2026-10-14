package com.ako.dbuff.service.replay.analysis.lane;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A side with a hero who had not reached the ultimate level when his laning ended, while every hero
 * of the other side had it, was hurt; the other way round, helped. Heroes whose level the track
 * does not say are left out. Such a hero may farm efficiently without being "in the lane".
 */
@Component
class UltimateContributor implements LaneReasonContributor {
  static final String CODE = LaneReason.NO_ULTIMATE_AT_LANE_END;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneHero> lacking = lacking(c, c.ownHeroes());
    List<LaneHero> enemyLacking = lacking(c, c.enemyHeroes());
    double severity = c.facts().properties().getNoUltimateSeverity();
    if (!lacking.isEmpty() && enemyLacking.isEmpty() && known(c, c.enemyHeroes())) {
      return List.of(
          LaneReason.hurt(
              CODE,
              lacking.get(0).slot(),
              severity,
              "%s had not reached level %d when the lane ended, %s had"
                  .formatted(names(c, lacking), level(c), enemyHeroes(c))));
    }
    if (lacking.isEmpty() && !enemyLacking.isEmpty() && known(c, c.ownHeroes())) {
      return List.of(
          LaneReason.helped(
              CODE,
              null,
              severity,
              "%s had not reached level %d when the lane ended, %s had"
                  .formatted(names(c, enemyLacking), level(c), ownHeroes(c))));
    }
    return List.of();
  }

  private static int level(LaneContext c) {
    return c.facts().properties().getUltimateLevel();
  }

  private static String enemyHeroes(LaneContext c) {
    return c.enemyName() + "'s heroes";
  }

  private static String ownHeroes(LaneContext c) {
    return c.teamName() + "'s heroes";
  }

  private static List<LaneHero> lacking(LaneContext c, List<LaneHero> heroes) {
    return heroes.stream()
        .filter(h -> c.facts().stats().get(h.slot()) != null)
        .filter(h -> c.facts().stats().get(h.slot()).level() > 0)
        .filter(h -> c.facts().stats().get(h.slot()).ultimateSeconds() == null)
        .toList();
  }

  private static boolean known(LaneContext c, List<LaneHero> heroes) {
    return !heroes.isEmpty()
        && heroes.stream()
            .allMatch(
                h ->
                    c.facts().stats().get(h.slot()) != null
                        && c.facts().stats().get(h.slot()).level() > 0);
  }

  private static String names(LaneContext c, List<LaneHero> heroes) {
    return LaneContext.list(heroes.stream().map(h -> c.name(h.slot())).toList());
  }
}
