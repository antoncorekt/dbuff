package com.ako.dbuff.service.replay.analysis.role;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The numeric functions of a {@link FunctionProfile}; the lane is a position, not a number. The
 * functions that are not {@link #scored()} describe a build but weigh in no role score.
 */
public enum RoleFunction {
  FARM_PRIORITY("farm priority", FunctionProfile::farmPriority, true),
  JUNGLE("jungle", FunctionProfile::jungle, true),
  ROAMING("roaming", FunctionProfile::roaming, true),
  VISION_DUTY("vision duty", FunctionProfile::visionDuty, true),
  INITIATION("initiation", FunctionProfile::initiation, true),
  DISABLE("disable", FunctionProfile::disable, true),
  SAVE_HEAL("save and heal", FunctionProfile::saveHeal, true),
  DAMAGE("damage", FunctionProfile::damage, true),
  DURABILITY("durability", FunctionProfile::durability, true),
  ESCAPE("escape", FunctionProfile::escape, false);

  private final String words;
  private final Function<FunctionProfile, Double> reader;
  private final boolean scored;

  RoleFunction(String words, Function<FunctionProfile, Double> reader, boolean scored) {
    this.words = words;
    this.reader = reader;
    this.scored = scored;
  }

  /** The functions that count in the score of a role. */
  public static List<RoleFunction> scoredFunctions() {
    return Arrays.stream(values()).filter(RoleFunction::scored).toList();
  }

  /** Whether the function counts in the score of a role; an escape is a bonus and does not. */
  public boolean scored() {
    return scored;
  }

  /** The function as a person would write it. */
  public String words() {
    return words;
  }

  Double valueIn(FunctionProfile profile) {
    return reader.apply(profile);
  }
}
