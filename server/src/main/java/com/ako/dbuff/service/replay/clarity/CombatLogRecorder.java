package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogFacts;
import com.ako.dbuff.service.replay.ModifierWhitelist;
import java.util.ArrayList;
import java.util.List;
import skadistats.clarity.model.CombatLogEntry;

/**
 * Keeps the combat-log entries that involve a hero or a building and drops the rest, which is most
 * of the roughly 57 thousand entries a game writes. Timestamps are kept on the demo clock until the
 * game start is known.
 */
final class CombatLogRecorder {
  private static final String UNKNOWN = "dota_unknown";
  private static final double CENTISECONDS = 100.0;

  private final List<CombatEvent> events = new ArrayList<>();

  void record(CombatLogEntry entry) {
    CombatEventKind kind = kind(entry.getType().name());
    if (kind == null || !keeps(kind, entry)) {
      return;
    }
    events.add(
        new CombatEvent(
            kind,
            entry.getTimestamp(),
            name(entry.getAttackerName()),
            name(entry.getTargetName()),
            name(entry.getInflictorName()),
            flags(entry),
            entry.getValue(),
            entry.getHealth(),
            aux(kind, entry),
            (int) Math.round(entry.getModifierDuration() * CENTISECONDS),
            (int) entry.getLocationX(),
            (int) entry.getLocationY(),
            assistMask(entry)));
  }

  /** The events with times counted from 0:00. */
  CombatLogFacts result(double gameStartTime) {
    List<CombatEvent> shifted = new ArrayList<>(events.size());
    for (CombatEvent e : events) {
      shifted.add(
          new CombatEvent(
              e.kind(),
              e.gameSecond() - gameStartTime,
              e.attacker(),
              e.target(),
              e.inflictor(),
              e.flags(),
              e.value(),
              e.health(),
              e.aux(),
              e.aux2(),
              e.x(),
              e.y(),
              e.assistMask()));
    }
    return new CombatLogFacts(shifted);
  }

  /** The gold or experience reason for those kinds, the stun duration in hundredths otherwise. */
  private static int aux(CombatEventKind kind, CombatLogEntry c) {
    return switch (kind) {
      case GOLD -> c.getGoldReason();
      case XP -> c.getXpReason();
      default -> (int) Math.round(c.getStunDuration() * CENTISECONDS);
    };
  }

  private static boolean keeps(CombatEventKind kind, CombatLogEntry c) {
    return switch (kind) {
      case DAMAGE, DEATH -> c.isAttackerHero() || c.isTargetHero();
      case ABILITY, ITEM -> c.isAttackerHero();
      case MODIFIER_ADD, MODIFIER_REMOVE ->
          c.isTargetHero() && ModifierWhitelist.keeps(c.getInflictorName(), c.getStunDuration());
      case GOLD, XP, BUILDING_KILL, BUYBACK -> true;
    };
  }

  private static CombatEventKind kind(String type) {
    return switch (type) {
      case "DOTA_COMBATLOG_DAMAGE" -> CombatEventKind.DAMAGE;
      case "DOTA_COMBATLOG_DEATH" -> CombatEventKind.DEATH;
      case "DOTA_COMBATLOG_ABILITY" -> CombatEventKind.ABILITY;
      case "DOTA_COMBATLOG_ITEM" -> CombatEventKind.ITEM;
      case "DOTA_COMBATLOG_GOLD" -> CombatEventKind.GOLD;
      case "DOTA_COMBATLOG_XP" -> CombatEventKind.XP;
      case "DOTA_COMBATLOG_MODIFIER_ADD" -> CombatEventKind.MODIFIER_ADD;
      case "DOTA_COMBATLOG_MODIFIER_REMOVE" -> CombatEventKind.MODIFIER_REMOVE;
      case "DOTA_COMBATLOG_TEAM_BUILDING_KILL" -> CombatEventKind.BUILDING_KILL;
      case "DOTA_COMBATLOG_BUYBACK" -> CombatEventKind.BUYBACK;
      default -> null;
    };
  }

  private static int flags(CombatLogEntry c) {
    return (c.isAttackerHero() ? CombatEvent.ATTACKER_HERO : 0)
        | (c.isTargetHero() ? CombatEvent.TARGET_HERO : 0)
        | (c.isAttackerIllusion() ? CombatEvent.ATTACKER_ILLUSION : 0)
        | (c.isTargetIllusion() ? CombatEvent.TARGET_ILLUSION : 0);
  }

  private static int assistMask(CombatLogEntry c) {
    int mask = 0;
    if (c.hasAssistPlayers()) {
      for (Object player : c.getAssistPlayers()) {
        mask |= 1 << ((Number) player).intValue();
      }
    }
    return mask;
  }

  private static String name(String raw) {
    return raw == null || UNKNOWN.equals(raw) ? "" : raw;
  }
}
