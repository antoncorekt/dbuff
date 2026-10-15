package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;

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
    Double durability,
    Double escape) {
  public static final FunctionProfile UNKNOWN =
      new FunctionProfile(null, null, null, null, null, null, null, null, null, null, null);

  /** A profile without an escape value, which only the intent stage knows. */
  public FunctionProfile(
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
    this(
        lane,
        farmPriority,
        jungle,
        roaming,
        visionDuty,
        initiation,
        disable,
        saveHeal,
        damage,
        durability,
        null);
  }

  /** The functions of a stored profile; the lane is not read. */
  public static FunctionProfile stored(PlayerRoleProfileDomain stored) {
    return new FunctionProfile(
        null,
        stored.getFarmPriority(),
        stored.getJungle(),
        stored.getRoaming(),
        stored.getVisionDuty(),
        stored.getInitiation(),
        stored.getDisable(),
        stored.getSaveHeal(),
        stored.getDamage(),
        stored.getDurability(),
        stored.getEscape());
  }

  /** The value of a function, null when unknown. */
  public Double of(RoleFunction function) {
    return function.valueIn(this);
  }
}
