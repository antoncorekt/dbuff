package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.replay.analysis.skillstat.RealSkills;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skills by the name a person types or picks: the display name or the internal name. Only real
 * skills are listed, so talents and generic bonuses never appear in a picker or match an input.
 */
@Component
@RequiredArgsConstructor
public class SkillNameIndex {
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  private final ConstantsManagers constants;
  private final AbilityLabels labels;

  /** Display name to internal name, for pickers. */
  public Map<String, String> displayToInternal() {
    Map<String, String> result = new LinkedHashMap<>();
    for (AbilityIdsConstant ability : realSkills()) {
      result.putIfAbsent(labels.label(ability.getName()), ability.getName());
    }
    return result;
  }

  public Optional<Long> resolve(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }
    String wanted = input.trim();
    return realSkills().stream()
        .filter(
            skill ->
                skill.getName().equalsIgnoreCase(wanted)
                    || labels.label(skill.getName()).equalsIgnoreCase(wanted))
        .map(AbilityIdsConstant::getId)
        .findFirst();
  }

  /** The display name nearest to what was typed, when one is close enough to be a typo of it. */
  public Optional<String> suggest(String input) {
    return TextSimilarity.closest(input, displayToInternal().keySet(), MAX_SUGGESTION_DISTANCE);
  }

  /** The ids of the real skills of a hero as the constants list them. */
  public List<Long> skillsOfHero(long heroId) {
    String heroName =
        Optional.ofNullable(constants.getHeroConstantMap().get(String.valueOf(heroId)))
            .map(HeroConstant::getName)
            .orElse(null);
    HeroesAbilityConstant kit =
        heroName == null ? null : constants.getHeroAbilitiesMap().get(heroName);
    if (kit == null || kit.getAbilities() == null) {
      return List.of();
    }
    Map<String, Long> idByName = new LinkedHashMap<>();
    realSkills().forEach(skill -> idByName.put(skill.getName(), skill.getId()));
    return kit.getAbilities().stream()
        .map(idByName::get)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private List<AbilityIdsConstant> realSkills() {
    return constants.getAbilityConstantMap().values().stream()
        .filter(skill -> skill.getId() != null && skill.getName() != null)
        .filter(skill -> RealSkills.isSkill(skill.getName()))
        .toList();
  }
}
