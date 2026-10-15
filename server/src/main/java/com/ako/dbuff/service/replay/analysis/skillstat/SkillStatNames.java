package com.ako.dbuff.service.replay.analysis.skillstat;

/** How a hero or a skill is called in words, by its OpenDota id. */
public interface SkillStatNames {
  String hero(long heroId);

  String skill(long abilityId);
}
