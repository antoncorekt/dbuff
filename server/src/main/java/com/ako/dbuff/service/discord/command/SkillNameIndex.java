package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.replay.analysis.skillstat.RealSkills;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skills and heroes by the name a person types or picks: the display name or the internal name.
 * Only real skills are listed, so talents and generic bonuses never appear in a picker or match an
 * input. The skill tables are built once per loaded constants map (the cached map instance changes
 * when the constants reload, as {@code ConstantAbilityLabels} also relies on), not per keystroke.
 */
@Component
@RequiredArgsConstructor
public class SkillNameIndex {
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  private final ConstantsManagers constants;
  private final AbilityLabels labels;
  private volatile Snapshot snapshot;

  private record Snapshot(
      Map<String, AbilityIdsConstant> source,
      Map<String, String> displayToInternal,
      Map<String, Long> idByName,
      Map<Long, String> nameById,
      Map<String, List<Long>> idsByLowerLabel) {}

  /**
   * Display name to internal name, for pickers. Skills that share a display name are listed with
   * their internal name in brackets so each stays selectable.
   */
  public Map<String, String> displayToInternal() {
    return current().displayToInternal();
  }

  /**
   * The skills a typed name can mean: an internal name matches exactly one; a display name matches
   * every skill that carries it, in internal-name order. Empty when nothing matches.
   */
  public List<Long> resolve(String input) {
    if (input == null || input.isBlank()) {
      return List.of();
    }
    Snapshot now = current();
    String wanted = input.trim().toLowerCase(Locale.ROOT);
    for (Map.Entry<String, Long> entry : now.idByName().entrySet()) {
      if (entry.getKey().equalsIgnoreCase(wanted)) {
        return List.of(entry.getValue());
      }
    }
    String bracketed = now.displayToInternal().get(input.trim());
    if (bracketed != null) {
      return List.of(now.idByName().get(bracketed));
    }
    return now.idsByLowerLabel().getOrDefault(wanted, List.of());
  }

  public Optional<String> internalName(long skillId) {
    return Optional.ofNullable(current().nameById().get(skillId));
  }

  /** The heroes a typed name can mean (internal or display name), by id so the order is fixed. */
  public List<Long> heroes(String input) {
    if (input == null || input.isBlank()) {
      return List.of();
    }
    String wanted = input.trim();
    return constants.getHeroConstantMap().values().stream()
        .filter(
            hero ->
                wanted.equalsIgnoreCase(hero.getName())
                    || wanted.equalsIgnoreCase(hero.getLocalized_name()))
        .map(HeroConstant::getId)
        .filter(Objects::nonNull)
        .map(Long::valueOf)
        .distinct()
        .sorted()
        .toList();
  }

  /** The display name nearest to what was typed, when one is close enough to be a typo of it. */
  public Optional<String> suggest(String input) {
    return TextSimilarity.closest(
        input, current().displayToInternal().keySet(), MAX_SUGGESTION_DISTANCE);
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
    Map<String, Long> idByName = current().idByName();
    return kit.getAbilities().stream()
        .map(idByName::get)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private Snapshot current() {
    Map<String, AbilityIdsConstant> source = constants.getAbilityConstantMap();
    Snapshot known = snapshot;
    if (known != null && known.source() == source) {
      return known;
    }
    Snapshot fresh = build(source);
    snapshot = fresh;
    return fresh;
  }

  private Snapshot build(Map<String, AbilityIdsConstant> source) {
    List<AbilityIdsConstant> skills =
        source.values().stream()
            .filter(skill -> skill.getId() != null && skill.getName() != null)
            .filter(skill -> RealSkills.isSkill(skill.getName()))
            .sorted(Comparator.comparing(AbilityIdsConstant::getName))
            .toList();
    Map<String, Long> idByName = new TreeMap<>();
    Map<Long, String> nameById = new TreeMap<>();
    Map<String, List<Long>> idsByLabel = new TreeMap<>();
    for (AbilityIdsConstant skill : skills) {
      idByName.put(skill.getName(), skill.getId());
      nameById.put(skill.getId(), skill.getName());
      idsByLabel
          .computeIfAbsent(
              labels.label(skill.getName()).toLowerCase(Locale.ROOT), key -> new ArrayList<>())
          .add(skill.getId());
    }
    Map<String, String> display = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (AbilityIdsConstant skill : skills) {
      String label = labels.label(skill.getName());
      boolean shared = idsByLabel.get(label.toLowerCase(Locale.ROOT)).size() > 1;
      display.put(shared ? label + " (" + skill.getName() + ")" : label, skill.getName());
    }
    return new Snapshot(source, display, idByName, nameById, idsByLabel);
  }
}
