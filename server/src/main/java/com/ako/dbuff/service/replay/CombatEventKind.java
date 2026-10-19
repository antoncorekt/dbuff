package com.ako.dbuff.service.replay;

/** The kinds of combat-log entries kept: the ones that involve a hero or a building. */
public enum CombatEventKind {
  DAMAGE,
  DEATH,
  ABILITY,
  ITEM,
  GOLD,
  XP,
  MODIFIER_ADD,
  MODIFIER_REMOVE,
  BUILDING_KILL,
  BUYBACK,
  HEAL
}
