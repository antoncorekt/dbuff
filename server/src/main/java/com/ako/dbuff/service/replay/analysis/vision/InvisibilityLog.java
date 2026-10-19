package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When each hero was invisible, from the combat log: the spans of the game's generic invisibility
 * modifier, and the use of a listed ability or item, which covers an invisibility the modifier
 * whitelist did not keep. A hero who owns a passive invisibility ability is invisible whenever he
 * is not attacking, so he counts as invisible in every fight he is in.
 */
public final class InvisibilityLog {
  static final String MODIFIER = "modifier_invisible";

  private final Map<Long, List<double[]>> spans = new HashMap<>();
  private final Map<Long, List<Double>> uses = new HashMap<>();
  private final InvisibleHeroes sources;

  public InvisibilityLog(
      List<CombatEvent> events, Map<String, Long> slotByHero, InvisibleHeroes sources) {
    this.sources = sources;
    Map<Long, Integer> depth = new HashMap<>();
    Map<Long, Double> since = new HashMap<>();
    for (CombatEvent e : events) {
      switch (e.kind()) {
        case MODIFIER_ADD, MODIFIER_REMOVE -> {
          Long slot = slotByHero.get(e.target());
          if (slot != null && MODIFIER.equals(e.inflictor())) {
            modifier(slot, e, depth, since);
          }
        }
        case ABILITY, ITEM -> {
          Long slot = slotByHero.get(e.attacker());
          boolean listed =
              e.kind() == CombatEventKind.ABILITY
                  ? sources.abilities().containsKey(e.inflictor())
                  : sources.items().contains(e.inflictor());
          if (slot != null && listed) {
            uses.computeIfAbsent(slot, s -> new ArrayList<>()).add(e.gameSecond());
          }
        }
        default -> {}
      }
    }
    since.forEach(
        (slot, from) ->
            spans
                .computeIfAbsent(slot, s -> new ArrayList<>())
                .add(new double[] {from, Double.MAX_VALUE}));
  }

  private void modifier(
      long slot, CombatEvent e, Map<Long, Integer> depth, Map<Long, Double> since) {
    int now = depth.getOrDefault(slot, 0);
    if (e.kind() == CombatEventKind.MODIFIER_ADD) {
      if (now == 0) {
        since.put(slot, e.gameSecond());
      }
      depth.put(slot, now + 1);
    } else if (now > 0) {
      depth.put(slot, now - 1);
      if (now == 1) {
        spans
            .computeIfAbsent(slot, s -> new ArrayList<>())
            .add(new double[] {since.remove(slot), e.gameSecond()});
      }
    }
  }

  /**
   * The slots among {@code slots} that were invisible in the fight: inside the modifier span at any
   * moment from {@code start} to {@code end}, a listed ability or item used from {@code lookback}
   * before the start to the end, or the owner of a passive ability.
   *
   * @param abilitiesBySlot the abilities each hero owned at the start
   */
  public Set<Long> invisibleIn(
      Set<Long> slots,
      double start,
      double end,
      double lookback,
      Map<Long, Set<String>> abilitiesBySlot) {
    Set<Long> found = new HashSet<>();
    for (long slot : slots) {
      boolean modifier =
          spans.getOrDefault(slot, List.of()).stream().anyMatch(s -> s[0] <= end && s[1] >= start);
      boolean used =
          uses.getOrDefault(slot, List.of()).stream()
              .anyMatch(t -> t >= start - lookback && t <= end);
      boolean passive =
          abilitiesBySlot.getOrDefault(slot, Set.of()).stream()
              .anyMatch(
                  a -> sources.abilities().containsKey(a) && sources.abilities().get(a).passive());
      if (modifier || used || passive) {
        found.add(slot);
      }
    }
    return found;
  }
}
