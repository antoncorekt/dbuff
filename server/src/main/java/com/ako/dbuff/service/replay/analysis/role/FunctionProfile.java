package com.ako.dbuff.service.replay.analysis.role;

/**
 * What a player does for his team, each function from 0 (nothing) to 1 (all he can); null when
 * nothing known tells it, which is not the same as 0. The functions are a hypothesis of the role
 * model.
 *
 * @param lane null when the stage cannot say (the hero alone does not pick a lane)
 */
public record FunctionProfile(
    LanePosition lane,
    Double farmPriority,
    Double jungle,
    Double roaming,
    Double visionDuty,
    Double initiation,
    Double disable,
    Double saveHeal,
    Double damage,
    Double durability) {
  public static final FunctionProfile UNKNOWN =
      new FunctionProfile(null, null, null, null, null, null, null, null, null, null);

  /** The value of a function, null when unknown. */
  public Double of(RoleFunction function) {
    return function.valueIn(this);
  }
}
