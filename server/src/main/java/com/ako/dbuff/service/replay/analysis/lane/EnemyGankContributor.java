package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Enemy heroes from other lanes (or with no lane) who killed a hero of the side, or did at least
 * the gank damage to the lane, while its heroes were laning.
 */
@Component
class EnemyGankContributor implements LaneReasonContributor {
  static final String CODE = "ENEMY_GANK";
  private static final double SEVERITY_PER_KILL = 0.25;
  private static final int MOST_KILLS_COUNTED = 2;
  private static final double DAMAGE_STEP = 0.15;
  private static final double DAMAGE_CAP = 0.5;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    LaneFacts facts = c.facts();
    TeamSide enemy = LanePair.other(c.side());
    List<Long> outsiders = facts.outsiders(enemy, c.pair().lane());
    long damage = 0;
    long kills = 0;
    Long main = null;
    long mainScore = -1;
    List<String> involved = new ArrayList<>();
    for (long outsider : outsiders) {
      long dealt =
          c.ownHeroes().stream().mapToLong(h -> facts.damage().takenFrom(h.slot(), outsider)).sum();
      long killed =
          facts.deaths().stream()
              .filter(d -> d.killerSlot() != null && d.killerSlot() == outsider)
              .filter(d -> diedLaning(c, d))
              .count();
      damage += dealt;
      if (dealt > 0 || killed > 0) {
        involved.add(c.name(outsider));
      }
      kills += killed;
      long score = killed * Integer.MAX_VALUE + dealt;
      if ((dealt > 0 || killed > 0) && score > mainScore) {
        main = outsider;
        mainScore = score;
      }
    }
    int gankDamage = facts.properties().getGankDamage();
    if (kills == 0 && damage < gankDamage) {
      return List.of();
    }
    double severity =
        SEVERITY_PER_KILL * Math.min(kills, MOST_KILLS_COUNTED)
            + Math.min(DAMAGE_CAP, damage / (double) gankDamage * DAMAGE_STEP);
    return List.of(
        LaneReason.hurt(
            CODE,
            main,
            severity,
            "%s, from other lanes, %sdid %d hero damage to the lane"
                .formatted(
                    LaneContext.list(involved),
                    kills > 0 ? "killed %d and ".formatted(kills) : "",
                    damage)));
  }

  private static boolean diedLaning(LaneContext c, LaneDeath death) {
    return c.ownHeroes().stream()
        .anyMatch(h -> h.slot() == death.victimSlot() && h.window().contains(death.seconds()));
  }
}
