package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;

/**
 * A function a team is expected to have in someone. The profile has no wave clear or frontline
 * function, so damage and durability stand in for them until the model gets their own.
 */
enum MissingFunction {
  NO_INITIATOR(RoleFunction.INITIATION, false),
  NO_DISABLE(RoleFunction.DISABLE, false),
  NO_WAVECLEAR(RoleFunction.DAMAGE, false),
  NO_FRONTLINE(RoleFunction.DURABILITY, false),
  NO_SAVE_HEAL(RoleFunction.SAVE_HEAL, false),
  NO_VISION_DUTY(RoleFunction.VISION_DUTY, true);

  private final RoleFunction function;
  private final boolean finalOnly;

  MissingFunction(RoleFunction function, boolean finalOnly) {
    this.function = function;
    this.finalOnly = finalOnly;
  }

  RoleFunction function() {
    return function;
  }

  /** Whether the token only makes sense after the match, as the draft cannot say it. */
  boolean finalOnly() {
    return finalOnly;
  }
}
