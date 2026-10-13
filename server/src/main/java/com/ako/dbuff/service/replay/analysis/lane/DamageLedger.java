package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hero damage of the laning windows: what each hero dealt to enemy heroes while he was laning,
 * what he took from them, who did it, and what he did to towers.
 */
final class DamageLedger {
  private static final String TOWER = "_tower";

  private final Map<Long, Long> dealt = new HashMap<>();
  private final Map<Long, Long> taken = new HashMap<>();
  private final Map<Long, Long> tower = new HashMap<>();
  private final Map<Long, Map<Long, Long>> takenFrom = new HashMap<>();

  DamageLedger(
      List<CombatEvent> events, Map<String, Long> slotByHero, Map<Long, LaningWindow> windows) {
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DAMAGE || !e.attackerIsRealHero()) {
        continue;
      }
      Long attacker = slotByHero.get(e.attacker());
      if (attacker == null) {
        continue;
      }
      if (e.target().contains(TOWER)) {
        if (inWindow(windows, attacker, e)) {
          tower.merge(attacker, (long) e.value(), Long::sum);
        }
        continue;
      }
      Long target = e.targetIsRealHero() ? slotByHero.get(e.target()) : null;
      if (target == null || TeamSide.ofSlot(target) == TeamSide.ofSlot(attacker)) {
        continue;
      }
      if (inWindow(windows, attacker, e)) {
        dealt.merge(attacker, (long) e.value(), Long::sum);
      }
      if (inWindow(windows, target, e)) {
        taken.merge(target, (long) e.value(), Long::sum);
        takenFrom
            .computeIfAbsent(target, k -> new HashMap<>())
            .merge(attacker, (long) e.value(), Long::sum);
      }
    }
  }

  private static boolean inWindow(Map<Long, LaningWindow> windows, long slot, CombatEvent e) {
    LaningWindow w = windows.get(slot);
    return w != null && w.contains(e.gameSecond());
  }

  long dealt(long slot) {
    return dealt.getOrDefault(slot, 0L);
  }

  long taken(long slot) {
    return taken.getOrDefault(slot, 0L);
  }

  long towerDamage(long slot) {
    return tower.getOrDefault(slot, 0L);
  }

  /** What {@code attacker} did to {@code target} while the target was laning. */
  long takenFrom(long target, long attacker) {
    return takenFrom.getOrDefault(target, Map.of()).getOrDefault(attacker, 0L);
  }
}
