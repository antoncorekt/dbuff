package com.ako.dbuff.service.replay;

/**
 * One combat-log entry. Names are the game's internal names; a missing one is the empty string.
 *
 * @param gameSecond seconds from 0:00, negative before the horn
 * @param flags see the {@code *_HERO} and {@code *_ILLUSION} constants
 * @param value damage, gold or experience, depending on the kind
 * @param health the target's health afterwards, for damage
 * @param aux the gold or experience reason, or a stun duration in hundredths of a second
 * @param aux2 a modifier duration in hundredths of a second
 * @param x world location, for gold and experience
 * @param assistMask player indexes (0-9) that assisted, one bit each, for deaths
 */
public record CombatEvent(
    CombatEventKind kind,
    double gameSecond,
    String attacker,
    String target,
    String inflictor,
    int flags,
    int value,
    int health,
    int aux,
    int aux2,
    int x,
    int y,
    int assistMask) {

  public static final int ATTACKER_HERO = 1;
  public static final int TARGET_HERO = 2;
  public static final int ATTACKER_ILLUSION = 4;
  public static final int TARGET_ILLUSION = 8;

  public boolean attackerIsRealHero() {
    return (flags & ATTACKER_HERO) != 0 && (flags & ATTACKER_ILLUSION) == 0;
  }

  public boolean targetIsRealHero() {
    return (flags & TARGET_HERO) != 0 && (flags & TARGET_ILLUSION) == 0;
  }
}
