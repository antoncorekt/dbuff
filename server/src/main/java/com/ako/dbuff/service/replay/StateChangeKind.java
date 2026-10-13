package com.ako.dbuff.service.replay;

public enum StateChangeKind {
  ITEM_ADDED,
  ITEM_REMOVED,
  /** An item or ability went on cooldown; {@code readyAtSeconds} says when it comes off. */
  COOLDOWN,
  ABILITY_LEVEL,
  /** An item or ability's mana cost, first seen or changed (it follows the skill level). */
  MANA_COST,
  /** The charge count of a whitelisted item, first seen above 0 or changed. */
  ITEM_CHARGES
}
