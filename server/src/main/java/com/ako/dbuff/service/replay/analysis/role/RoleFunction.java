package com.ako.dbuff.service.replay.analysis.role;

import java.util.function.Function;

/** The numeric functions of a {@link FunctionProfile}; the lane is a position, not a number. */
public enum RoleFunction {
  FARM_PRIORITY(FunctionProfile::farmPriority),
  JUNGLE(FunctionProfile::jungle),
  ROAMING(FunctionProfile::roaming),
  VISION_DUTY(FunctionProfile::visionDuty),
  INITIATION(FunctionProfile::initiation),
  DISABLE(FunctionProfile::disable),
  SAVE_HEAL(FunctionProfile::saveHeal),
  DAMAGE(FunctionProfile::damage),
  DURABILITY(FunctionProfile::durability);

  private final Function<FunctionProfile, Double> reader;

  RoleFunction(Function<FunctionProfile, Double> reader) {
    this.reader = reader;
  }

  Double valueIn(FunctionProfile profile) {
    return reader.apply(profile);
  }
}
