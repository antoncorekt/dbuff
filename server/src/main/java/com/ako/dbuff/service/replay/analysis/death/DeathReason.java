package com.ako.dbuff.service.replay.analysis.death;

/** The first thing that explains a death, in the order the rules are checked. */
public enum DeathReason {
  /** The hero came back at once by Aegis or reincarnation; not a real death. */
  REINCARNATED,
  /** Spell immunity was on and the hero died anyway. */
  DIED_IMMUNE,
  /** Spell immunity ended just before the death. */
  BKB_EXPIRED,
  /** BKB was owned and ready but not used in the seconds before. */
  BKB_READY_UNUSED,
  /** BKB was owned but cooling down. */
  BKB_ON_COOLDOWN,
  DISABLE_CHAIN,
  CAUGHT_OUT,
  TOWER,
  BURST,
  /** Killed by a hero with no BKB owned, and nothing more specific explains it. */
  NO_BKB,
  OTHER
}
