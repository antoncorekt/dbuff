package com.ako.dbuff.service.replay;

public enum StateChangeKind {
  ITEM_ADDED,
  ITEM_REMOVED,
  /** An item or ability went on cooldown; {@code readyAtSeconds} says when it comes off. */
  COOLDOWN,
  ABILITY_LEVEL
}
