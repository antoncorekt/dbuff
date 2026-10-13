package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the combat log by player slot (0-4 Radiant, 128-132 Dire) instead of by hero name. */
final class CombatSlots {
  private static final int PLAYERS = 10;

  private CombatSlots() {}

  /** The slot of each hero name the log shares with the roster. */
  static Map<String, Long> byHero(List<CombatEvent> events, List<ReplayPlayerDomain> roster) {
    Map<String, Long> slotByHero = new HashMap<>();
    CombatHeroes.playerIndexByHero(events, roster)
        .forEach((hero, index) -> slotByHero.put(hero, PlayerSlots.slot(index)));
    return slotByHero;
  }

  /**
   * When each hero killed or assisted in the death of an enemy hero, by slot; an Aegis revival
   * counts as a death here, as in the log.
   */
  static Map<Long, List<Double>> kills(List<CombatEvent> events, Map<String, Long> slotByHero) {
    Map<Long, List<Double>> kills = new HashMap<>();
    for (CombatEvent e : events) {
      Long victim = slotByHero.get(e.target());
      if (e.kind() != CombatEventKind.DEATH || !e.targetIsRealHero() || victim == null) {
        continue;
      }
      Set<Long> takers = new HashSet<>();
      Long killer = slotByHero.get(e.attacker());
      if (killer != null && e.attackerIsRealHero()) {
        takers.add(killer);
      }
      for (int index = 0; index < PLAYERS; index++) {
        if ((e.assistMask() & (1 << index)) != 0) {
          takers.add(PlayerSlots.slot(index));
        }
      }
      for (Long taker : takers) {
        if (TeamSide.ofSlot(taker) != TeamSide.ofSlot(victim)) {
          kills.computeIfAbsent(taker, s -> new ArrayList<>()).add(e.gameSecond());
        }
      }
    }
    return kills;
  }
}
