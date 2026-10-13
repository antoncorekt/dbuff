package com.ako.dbuff.service.replay.analysis.lane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * A hero of the lane died, or killed a hero of the other side, before the early limit. One reason
 * per hero: the severity is that of his first one, the evidence counts them all.
 */
@Component
class EarlyKillContributor implements LaneReasonContributor {
  static final String DEATH = "EARLY_DEATH";
  static final String KILL = "EARLY_KILL";
  private static final double SEVERITY_FLOOR = 0.3;
  private static final double SEVERITY_RANGE = 0.3;

  @Override
  public List<LaneReason> contribute(LaneContext c) {
    int limit = c.facts().properties().getEarlyKillSeconds();
    Map<Long, List<LaneDeath>> died = new TreeMap<>();
    Map<Long, List<LaneDeath>> killed = new TreeMap<>();
    for (LaneDeath death : c.facts().deaths()) {
      if (death.seconds() >= limit || death.seconds() < 0) {
        continue;
      }
      if (isLaning(c, c.ownHeroes(), death.victimSlot(), death.seconds())) {
        died.computeIfAbsent(death.victimSlot(), k -> new ArrayList<>()).add(death);
      } else if (death.killerSlot() != null
          && isLaning(c, c.enemyHeroes(), death.victimSlot(), death.seconds())
          && isLaning(c, c.ownHeroes(), death.killerSlot(), death.seconds())) {
        killed.computeIfAbsent(death.killerSlot(), k -> new ArrayList<>()).add(death);
      }
    }
    List<LaneReason> reasons = new ArrayList<>();
    died.forEach(
        (slot, deaths) ->
            reasons.add(
                LaneReason.hurt(
                    DEATH,
                    slot,
                    severity(deaths, limit),
                    "%s died %s"
                        .formatted(
                            c.name(slot), times(deaths, d -> LaneContext.clock(d.seconds()))))));
    killed.forEach(
        (slot, deaths) ->
            reasons.add(
                LaneReason.helped(
                    KILL,
                    slot,
                    severity(deaths, limit),
                    "%s killed %s"
                        .formatted(
                            c.name(slot),
                            LaneContext.list(
                                deaths.stream()
                                    .sorted(Comparator.comparingDouble(LaneDeath::seconds))
                                    .map(
                                        d ->
                                            "%s at %s"
                                                .formatted(
                                                    c.name(d.victimSlot()),
                                                    LaneContext.clock(d.seconds())))
                                    .toList())))));
    return reasons;
  }

  private static double severity(List<LaneDeath> deaths, int limit) {
    double first = deaths.stream().mapToDouble(LaneDeath::seconds).min().orElse(limit);
    return SEVERITY_FLOOR + SEVERITY_RANGE * (1 - first / limit);
  }

  private static String times(List<LaneDeath> deaths, Function<LaneDeath, String> clock) {
    List<String> clocks =
        deaths.stream().sorted(Comparator.comparingDouble(LaneDeath::seconds)).map(clock).toList();
    return deaths.size() == 1
        ? "at " + clocks.get(0)
        : "%d times, at %s".formatted(deaths.size(), LaneContext.list(clocks));
  }

  private static boolean isLaning(LaneContext c, List<LaneHero> heroes, long slot, double second) {
    return heroes.stream().anyMatch(h -> h.slot() == slot && h.window().contains(second));
  }
}
