package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a team had to see invisible heroes with, from the combat log and the inventory changes: dust
 * used, and dust, a gem or a sentry ward held by the heroes in the fight when it began. Oracle's
 * Fortune's End and other rare detection are not read.
 */
final class DetectionLog {
  static final String DUST = "item_dust";
  static final String GEM = "item_gem";
  static final String SENTRY = "item_ward_sentry";
  private static final Set<String> HELD = Set.of(DUST, GEM, SENTRY);

  private final Map<Long, List<Double>> dustUses = new HashMap<>();
  private final Map<Long, Map<String, List<double[]>>> holdings = new HashMap<>();

  DetectionLog(
      List<CombatEvent> events,
      Map<String, Long> slotByHero,
      List<ReplayStateChangeDomain> stateChanges) {
    for (CombatEvent e : events) {
      Long slot = slotByHero.get(e.attacker());
      if (e.kind() == CombatEventKind.ITEM && DUST.equals(e.inflictor()) && slot != null) {
        dustUses.computeIfAbsent(slot, s -> new ArrayList<>()).add(e.gameSecond());
      }
    }
    Map<Long, Map<String, Integer>> count = new HashMap<>();
    stateChanges.stream()
        .filter(
            c ->
                c.getPlayerSlot() != null
                    && c.getGameSeconds() != null
                    && HELD.contains(c.getName()))
        .filter(
            c ->
                StateChangeKind.ITEM_ADDED.name().equals(c.getKind())
                    || StateChangeKind.ITEM_REMOVED.name().equals(c.getKind()))
        .sorted(Comparator.comparing(ReplayStateChangeDomain::getGameSeconds))
        .forEach(
            c -> {
              int delta = StateChangeKind.ITEM_ADDED.name().equals(c.getKind()) ? 1 : -1;
              int now =
                  count
                      .computeIfAbsent(c.getPlayerSlot(), s -> new HashMap<>())
                      .merge(c.getName(), delta, (a, b) -> Math.max(0, a + b));
              holdings
                  .computeIfAbsent(c.getPlayerSlot(), s -> new HashMap<>())
                  .computeIfAbsent(c.getName(), n -> new ArrayList<>())
                  .add(new double[] {c.getGameSeconds(), now});
            });
  }

  /** Whether any of the slots used dust from {@code from} to {@code to}. */
  boolean dustUsed(Set<Long> slots, double from, double to) {
    return slots.stream()
        .anyMatch(
            s -> dustUses.getOrDefault(s, List.of()).stream().anyMatch(t -> t >= from && t <= to));
  }

  /** Which of dust, gem and sentry the slots held at the time, as short words in a fixed order. */
  Set<String> held(Set<Long> slots, double time) {
    Set<String> held = new TreeSet<>();
    for (long slot : slots) {
      holdings
          .getOrDefault(slot, Map.of())
          .forEach(
              (item, changes) -> {
                int now = 0;
                for (double[] change : changes) {
                  if (change[0] > time) {
                    break;
                  }
                  now = (int) change[1];
                }
                if (now > 0) {
                  held.add(item.substring("item_".length()).replace("ward_", ""));
                }
              });
    }
    return held;
  }
}
