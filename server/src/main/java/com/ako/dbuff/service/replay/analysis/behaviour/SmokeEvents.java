package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the smoke uses, the smoke modifiers and the Roshan kills out of the combat log. */
final class SmokeEvents {
  static final String ITEM = "item_smoke_of_deceit";
  static final String MODIFIER = "modifier_smoke_of_deceit";
  private static final String ROSHAN = "npc_dota_roshan";
  private static final double CENTISECONDS = 100.0;

  /** A use of the item by the hero of {@code userSlot}. */
  record Use(long userSlot, double seconds) {}

  private SmokeEvents() {}

  /** The uses of the item; the log records them for hero users only. */
  static List<Use> uses(List<CombatEvent> events, Map<String, Long> slotByHero) {
    return events.stream()
        .filter(e -> e.kind() == CombatEventKind.ITEM && ITEM.equals(e.inflictor()))
        .filter(e -> slotByHero.containsKey(e.attacker()))
        .map(e -> new Use(slotByHero.get(e.attacker()), e.gameSecond()))
        .toList();
  }

  /** The modifier of each hero from its add to its next removal; the log must be in time order. */
  static List<SmokeHold> holds(List<CombatEvent> events, Map<String, Long> slotByHero) {
    Map<Long, CombatEvent> open = new HashMap<>();
    List<SmokeHold> holds = new ArrayList<>();
    for (CombatEvent e : events) {
      Long slot = slotByHero.get(e.target());
      if (slot == null || !MODIFIER.equals(e.inflictor())) {
        continue;
      }
      if (e.kind() == CombatEventKind.MODIFIER_ADD) {
        open.putIfAbsent(slot, e);
      } else if (e.kind() == CombatEventKind.MODIFIER_REMOVE && open.containsKey(slot)) {
        holds.add(hold(slot, open.remove(slot), e.gameSecond()));
      }
    }
    open.forEach((slot, added) -> holds.add(hold(slot, added, null)));
    return holds;
  }

  /** When each team killed Roshan, by the team of the hero who struck the last blow. */
  static Map<TeamSide, List<Double>> roshanKills(
      List<CombatEvent> events, Map<String, Long> slotByHero) {
    Map<TeamSide, List<Double>> kills = new HashMap<>();
    for (CombatEvent e : events) {
      Long killer = slotByHero.get(e.attacker());
      if (e.kind() == CombatEventKind.DEATH
          && ROSHAN.equals(e.target())
          && killer != null
          && e.attackerIsRealHero()) {
        kills.computeIfAbsent(TeamSide.ofSlot(killer), t -> new ArrayList<>()).add(e.gameSecond());
      }
    }
    return kills;
  }

  private static SmokeHold hold(long slot, CombatEvent added, Double removed) {
    return new SmokeHold(slot, added.gameSecond(), removed, added.aux2() / CENTISECONDS);
  }
}
