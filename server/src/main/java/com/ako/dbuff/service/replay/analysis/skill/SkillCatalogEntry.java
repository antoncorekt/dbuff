package com.ako.dbuff.service.replay.analysis.skill;

import java.util.List;
import java.util.Map;

/**
 * One entry of {@code replay/skills.yaml}: a skill, the aspects that judge its casts and their
 * numbers.
 *
 * @param key the name stored with the rows
 * @param abilities the inflictor names of the skill in the combat log, e.g. {@code
 *     oracle_false_promise}
 * @param aspects the {@link SkillAspect} names that judge the skill
 * @param parameters the file's defaults with this entry's own values laid over them
 */
public record SkillCatalogEntry(
    String key, List<String> abilities, List<String> aspects, Map<String, Double> parameters) {

  public double parameter(String name, double fallback) {
    return parameters.getOrDefault(name, fallback);
  }
}
