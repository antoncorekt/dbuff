package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The mid lane only: the power, water and wisdom runes the mid heroes of each side took while
 * laning (a bounty rune is gold and is not counted). More runes for the side helped it; more for
 * the other side hurt it.
 */
@Component
class MidRuneContributor implements LaneReasonContributor {
  static final String CODE = "MID_RUNES";
  private static final double SEVERITY_PER_RUNE = 0.2;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    if (c.pair().lane() != Zone.Lane.MID) {
      return List.of();
    }
    List<RunePickupDomain> own = taken(c, c.ownHeroes());
    List<RunePickupDomain> enemy = taken(c, c.enemyHeroes());
    int difference = own.size() - enemy.size();
    if (difference == 0) {
      return List.of();
    }
    List<RunePickupDomain> shown = difference > 0 ? own : enemy;
    String evidence =
        "%s took %d runes in the mid lane: %s"
            .formatted(
                difference > 0 ? c.teamName() : c.enemyName(),
                shown.size(),
                LaneContext.list(
                    shown.stream()
                        .map(
                            p ->
                                "%s at %s"
                                    .formatted(
                                        Enums.find(RuneType.class, p.getRuneType()).words(),
                                        LaneContext.clock(p.getSeconds())))
                        .toList()));
    double severity = SEVERITY_PER_RUNE * Math.abs(difference);
    Long subject = shown.get(0).getPlayerSlot();
    return List.of(
        difference > 0
            ? LaneReason.helped(CODE, subject, severity, evidence)
            : LaneReason.hurt(CODE, subject, severity, evidence));
  }

  private static List<RunePickupDomain> taken(LaneContext c, List<LaneHero> heroes) {
    return c.facts().runes().stream()
        .filter(p -> p.getSeconds() != null && p.getPlayerSlot() != null)
        .filter(p -> counts(Enums.find(RuneType.class, p.getRuneType())))
        .filter(
            p ->
                heroes.stream()
                    .anyMatch(
                        h -> h.slot() == p.getPlayerSlot() && h.window().contains(p.getSeconds())))
        .toList();
  }

  private static boolean counts(RuneType type) {
    return type != null
        && (type.isFightEffect() || type == RuneType.WATER || type == RuneType.WISDOM);
  }
}
