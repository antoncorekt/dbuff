package com.ako.dbuff.service.replay.analysis.skillstat;

/** Which stored ability names are skills; talents and generic attribute bonuses are not. */
public final class RealSkills {
  private RealSkills() {}

  public static boolean isSkill(String name) {
    return name == null || !(name.contains("special_bonus_") || name.contains("attribute_bonus"));
  }
}
