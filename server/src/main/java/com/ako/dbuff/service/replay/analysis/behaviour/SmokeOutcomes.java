package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.CombatSlots.HeroKill;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeEvents.RoshanKill;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Judges what a smoke came to from what its group did inside the smoke's window. */
final class SmokeOutcomes {
  /**
   * @param kills the deaths of heroes with who killed or assisted
   * @param deaths when each hero died (an Aegis revival excluded), by slot
   * @param buildings the towers and barracks that fell
   * @param roshanKills the Roshan kills by hero
   */
  record Events(
      List<HeroKill> kills,
      Map<Long, List<Double>> deaths,
      List<TowerFall> buildings,
      List<RoshanKill> roshanKills) {}

  private SmokeOutcomes() {}

  /**
   * PAID_OFF when, between {@code from} and {@code to}, a group member killed or assisted in the
   * death of an enemy hero, a group member was among the attackers of an enemy tower, barracks or
   * ancient (not a deny), or a group member killed Roshan. Otherwise BACKFIRED when a hero of the
   * group died, else NOTHING. What teammates outside the group did does not count.
   */
  static SmokeResult of(
      TeamSide team, Collection<Long> group, double from, double to, Events events) {
    int kills =
        (int)
            events.kills().stream()
                .filter(k -> within(k.seconds(), from, to))
                .filter(k -> k.takers().stream().anyMatch(group::contains))
                .count();
    int buildings =
        (int)
            events.buildings().stream()
                .filter(b -> b.owner() == team.opponent() && !b.denied())
                .filter(b -> within(b.seconds(), from, to))
                .filter(b -> b.attackers().stream().anyMatch(group::contains))
                .count();
    int roshan =
        (int)
            events.roshanKills().stream()
                .filter(r -> group.contains(r.slot()) && within(r.seconds(), from, to))
                .count();
    int groupDeaths =
        (int)
            group.stream()
                .flatMap(slot -> events.deaths().getOrDefault(slot, List.of()).stream())
                .filter(t -> within(t, from, to))
                .count();
    int enemyDeaths =
        (int)
            events.deaths().entrySet().stream()
                .filter(e -> TeamSide.ofSlot(e.getKey()) == team.opponent())
                .flatMap(e -> e.getValue().stream())
                .filter(t -> within(t, from, to))
                .count();
    return new SmokeResult(
        outcome(kills + buildings + roshan, groupDeaths),
        kills,
        buildings,
        roshan,
        groupDeaths,
        enemyDeaths);
  }

  private static SmokeOutcome outcome(int gains, int groupDeaths) {
    if (gains > 0) {
      return SmokeOutcome.PAID_OFF;
    }
    return groupDeaths > 0 ? SmokeOutcome.BACKFIRED : SmokeOutcome.NOTHING;
  }

  private static boolean within(double seconds, double from, double to) {
    return seconds >= from && seconds <= to;
  }
}
