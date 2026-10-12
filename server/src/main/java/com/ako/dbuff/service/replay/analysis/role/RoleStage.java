package com.ako.dbuff.service.replay.analysis.role;

/** The point of view a role is worked out from. */
public enum RoleStage {
  /** What the draft says the player was meant to do: the hero and the skills he holds. */
  INTENT,
  /** What the player did in the match. */
  FINAL
}
