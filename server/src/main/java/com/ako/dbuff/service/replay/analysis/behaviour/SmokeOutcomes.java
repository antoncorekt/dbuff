package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Judges what a smoke came to from the deaths, kills and objectives inside its window. */
final class SmokeOutcomes {
  /**
   * @param kills when each hero killed or assisted in the death of an enemy hero, by slot
   * @param deaths when each hero died (an Aegis revival excluded), by slot
   * @param buildings the towers and barracks that fell
   * @param roshanKills when each team killed Roshan
   */
  record Events(
      Map<Long, List<Double>> kills,
      Map<Long, List<Double>> deaths,
      List<TowerFall> buildings,
      Map<TeamSide, List<Double>> roshanKills) {}

  private SmokeOutcomes() {}

  /**
   * PAID_OFF when the group killed an enemy hero, an enemy tower or barracks fell, or the team
   * killed Roshan between {@code from} and {@code to}; else BACKFIRED when a hero of the group died
   * and no enemy hero did; else NOTHING.
   */
  static SmokeResult of(
      TeamSide team, Collection<Long> group, double from, double to, Events events) {
    int kills =
        (int)
            group.stream()
                .flatMap(slot -> times(events.kills(), slot))
                .filter(t -> within(t, from, to))
                .distinct()
                .count();
    int buildings =
        (int)
            events.buildings().stream()
                .filter(b -> b.owner() == team.opponent() && !b.denied())
                .filter(b -> within(b.seconds(), from, to))
                .count();
    int roshan =
        (int)
            events.roshanKills().getOrDefault(team, List.of()).stream()
                .filter(t -> within(t, from, to))
                .count();
    int groupDeaths =
        (int)
            group.stream()
                .flatMap(slot -> times(events.deaths(), slot))
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
        outcome(kills + buildings + roshan, groupDeaths, enemyDeaths),
        kills,
        buildings,
        roshan,
        groupDeaths,
        enemyDeaths);
  }

  private static SmokeOutcome outcome(int gains, int groupDeaths, int enemyDeaths) {
    if (gains > 0) {
      return SmokeOutcome.PAID_OFF;
    }
    return groupDeaths > 0 && enemyDeaths == 0 ? SmokeOutcome.BACKFIRED : SmokeOutcome.NOTHING;
  }

  private static Stream<Double> times(Map<Long, List<Double>> bySlot, long slot) {
    return bySlot.getOrDefault(slot, List.of()).stream();
  }

  private static boolean within(double seconds, double from, double to) {
    return seconds >= from && seconds <= to;
  }
}
