package com.ako.dbuff.service.replay.analysis.role;

import java.util.function.Function;

/** The numeric functions of a {@link FunctionProfile}; the lane is a position, not a number. */
public enum RoleFunction {
  FARM_PRIORITY("farm priority", FunctionProfile::farmPriority),
  JUNGLE("jungle", FunctionProfile::jungle),
  ROAMING("roaming", FunctionProfile::roaming),
  VISION_DUTY("vision duty", FunctionProfile::visionDuty),
  INITIATION("initiation", FunctionProfile::initiation),
  DISABLE("disable", FunctionProfile::disable),
  SAVE_HEAL("save and heal", FunctionProfile::saveHeal),
  DAMAGE("damage", FunctionProfile::damage),
  DURABILITY("durability", FunctionProfile::durability);

  private final String words;
  private final Function<FunctionProfile, Double> reader;

  RoleFunction(String words, Function<FunctionProfile, Double> reader) {
    this.words = words;
    this.reader = reader;
  }

  /** The function as a person would write it. */
  public String words() {
    return words;
  }

  Double valueIn(FunctionProfile profile) {
    return reader.apply(profile);
  }
}
