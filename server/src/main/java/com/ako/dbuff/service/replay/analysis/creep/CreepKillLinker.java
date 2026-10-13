package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.GoldReason;
import com.ako.dbuff.service.replay.XpReason;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the creeps heroes killed in a combat log and ties each to the gold and experience entries
 * written with it. The game writes a kill's gold and experience entries just before its DEATH row,
 * at the same moment, so the entries between the previous DEATH row and this one belong to this
 * kill. A flagbearer also pays a bonus after its DEATH row. When one kill has two gold entries of
 * the killer before it, which one is its own is unknown and the gold stays null.
 */
public final class CreepKillLinker {
  private static final double CENTISECONDS = 100.0;

  private CreepKillLinker() {}

  /**
   * @param halfOf the half of the map each hero's team starts in, by combat-log hero name
   */
  public static List<CreepKill> link(List<CombatEvent> events, Map<String, Zone.Half> halfOf) {
    List<CreepKill> result = new ArrayList<>();
    for (int i = 0; i < events.size(); i++) {
      CombatEvent kill = events.get(i);
      CreepClass creep = CreepClass.of(kill.target());
      if (kill.kind() != CombatEventKind.DEATH
          || creep == null
          || !kill.attackerIsRealHero()
          || !halfOf.containsKey(kill.attacker())) {
        continue;
      }
      Zone.Half own = halfOf.get(kill.attacker());
      List<CombatEvent> gold = new ArrayList<>();
      List<CombatEvent> xp = new ArrayList<>();
      int recipients = 0;
      for (int j = i - 1; j >= 0 && sameMoment(events.get(j), kill); j--) {
        CombatEvent e = events.get(j);
        if (e.kind() == CombatEventKind.DEATH) {
          break;
        }
        if (isCreepGold(e) && e.target().equals(kill.attacker())) {
          gold.add(e);
        } else if (isCreepXp(e)) {
          recipients++;
          if (e.target().equals(kill.attacker())) {
            xp.add(e);
          }
        }
      }
      CombatEvent paid = gold.size() == 1 ? gold.get(0) : null;
      CombatEvent experience = xp.size() == 1 ? xp.get(0) : null;
      CombatEvent located = paid != null ? paid : experience;
      result.add(
          new CreepKill(
              kill.attacker(),
              kill.gameSecond(),
              creep,
              kill.target(),
              side(kill.target(), own),
              creep.isLane() && CreepClass.sideOf(kill.target()) == own,
              paid == null ? null : paid.value(),
              creep.isFlagbearer() ? bonus(events, i) : null,
              experience == null ? null : experience.value(),
              experience == null ? null : recipients,
              located == null ? null : located.x(),
              located == null ? null : located.y()));
    }
    return result;
  }

  /**
   * The gold the killer got after the flagbearer's DEATH row, in the same moment and before any
   * later kill of that moment, whose own gold would come first. Null when there was none.
   */
  private static Integer bonus(List<CombatEvent> events, int death) {
    CombatEvent kill = events.get(death);
    int sum = 0;
    boolean found = false;
    for (int j = death + 1; j < events.size() && sameMoment(events.get(j), kill); j++) {
      CombatEvent e = events.get(j);
      if (e.kind() == CombatEventKind.DEATH) {
        return null;
      }
      if (isCreepGold(e) && e.target().equals(kill.attacker())) {
        sum += e.value();
        found = true;
      }
    }
    return found ? sum : null;
  }

  private static boolean isCreepGold(CombatEvent e) {
    return e.kind() == CombatEventKind.GOLD
        && (e.goldReason() == GoldReason.CREEP_KILL || e.goldReason() == GoldReason.NEUTRAL_KILL);
  }

  private static boolean isCreepXp(CombatEvent e) {
    return e.kind() == CombatEventKind.XP && e.xpReason() == XpReason.CREEP_KILL;
  }

  private static boolean sameMoment(CombatEvent a, CombatEvent b) {
    return Math.round(a.gameSecond() * CENTISECONDS) == Math.round(b.gameSecond() * CENTISECONDS);
  }

  private static ActivitySegment.Side side(String creep, Zone.Half own) {
    Zone.Half side = CreepClass.sideOf(creep);
    if (side == null) {
      return null;
    }
    return side == own ? ActivitySegment.Side.OWN : ActivitySegment.Side.ENEMY;
  }
}
