package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** When each player's hero killed a hero or helped to, by player index 0-9. */
record MidTally(Map<Integer, List<Double>> kills, Map<Integer, List<Double>> assists) {
  private static final int PLAYERS = 10;

  static MidTally of(List<CombatEvent> events, Map<String, Integer> playerOf) {
    Map<Integer, List<Double>> kills = new HashMap<>();
    Map<Integer, List<Double>> assists = new HashMap<>();
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DEATH || !e.targetIsRealHero()) {
        continue;
      }
      Integer killer = e.attackerIsRealHero() ? playerOf.get(e.attacker()) : null;
      if (killer != null) {
        kills.computeIfAbsent(killer, k -> new ArrayList<>()).add(e.gameSecond());
      }
      for (int player = 0; player < PLAYERS; player++) {
        if ((e.assistMask() >> player & 1) != 0) {
          assists.computeIfAbsent(player, k -> new ArrayList<>()).add(e.gameSecond());
        }
      }
    }
    return new MidTally(kills, assists);
  }

  /** Times in {@code [from, to)}. */
  static long count(List<Double> times, double from, double to) {
    return times == null ? 0 : times.stream().filter(t -> t >= from && t < to).count();
  }
}
