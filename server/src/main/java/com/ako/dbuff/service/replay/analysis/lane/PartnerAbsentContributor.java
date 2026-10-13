package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A lane partner who arrived late or left early (the time his window misses of the side's window,
 * when it is at least the gap property), or a lone hero whose support laned nowhere because he
 * roamed or jungled.
 */
@Component
class PartnerAbsentContributor implements LaneReasonContributor {
  static final String CODE = "PARTNER_ABSENT";
  static final String ROAMING = "PARTNER_ROAMING";

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    List<LaneReason> reasons = new ArrayList<>();
    List<LaneHero> own = c.ownHeroes();
    if (own.size() > 1) {
      int sideStart = own.stream().mapToInt(h -> h.window().startSeconds()).min().orElse(0);
      int sideEnd = own.stream().mapToInt(h -> h.window().endSeconds()).max().orElse(0);
      for (LaneHero hero : own) {
        int late = hero.window().startSeconds() - sideStart;
        int early = sideEnd - hero.window().endSeconds();
        int missing = late + early;
        if (missing >= c.facts().properties().getPartnerGapSeconds()) {
          reasons.add(
              LaneReason.hurt(
                  CODE,
                  hero.slot(),
                  missing / (double) Math.max(1, sideEnd - sideStart),
                  "%s was away %d s of the %d s his side laned%s"
                      .formatted(
                          c.name(hero.slot()),
                          missing,
                          sideEnd - sideStart,
                          early > 0
                              ? ", leaving at " + LaneContext.clock(hero.window().endSeconds())
                              : "")));
        }
      }
    } else if (own.size() == 1 && c.pair().lane() != Zone.Lane.MID) {
      c.facts().withoutLane().stream()
          .filter(slot -> TeamSide.ofSlot(slot) == c.side() && c.facts().isSupport(slot))
          .sorted()
          .findFirst()
          .ifPresent(
              slot ->
                  reasons.add(
                      LaneReason.hurt(
                          ROAMING,
                          slot,
                          0.5,
                          "%s laned alone; his support %s had no lane (roaming or jungling)"
                              .formatted(c.name(own.get(0).slot()), c.name(slot)))));
    }
    return reasons;
  }
}
