package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.discord.command.TextSimilarity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves user-supplied item, hero, and ability names to numeric IDs, reporting anything it could
 * not resolve rather than discarding it.
 *
 * <p>Matching is case-insensitive and accepts either the internal name or the display name, because
 * Discord autocomplete submits internal names while a user typing freehand will use display names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstantNameResolver {

  /**
   * Maximum edit distance for a suggestion to be offered. Beyond this the "did you mean" is noise
   * rather than help.
   */
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  private final ConstantsManagers constantsManagers;

  public NameResolution resolveItems(Set<String> names) {
    return resolve(names, this::itemIdFor);
  }

  public NameResolution resolveHeroes(Set<String> names) {
    return resolve(names, this::heroIdFor);
  }

  public NameResolution resolveAbilities(Set<String> names) {
    return resolve(names, this::abilityIdFor);
  }

  public Optional<String> suggestItem(String unknown) {
    return closest(unknown, itemSuggestionCandidates());
  }

  public Optional<String> suggestHero(String unknown) {
    return closest(unknown, heroSuggestionCandidates());
  }

  public Optional<String> suggestAbility(String unknown) {
    return closest(unknown, abilitySuggestionCandidates());
  }

  private NameResolution resolve(Set<String> names, Function<String, Optional<Long>> lookup) {
    if (names == null || names.isEmpty()) {
      return NameResolution.empty();
    }

    Set<Long> resolved = new LinkedHashSet<>();
    Set<String> unresolved = new LinkedHashSet<>();

    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      String trimmed = name.trim();
      lookup.apply(trimmed).ifPresentOrElse(resolved::add, () -> unresolved.add(trimmed));
    }

    if (!unresolved.isEmpty()) {
      log.debug("Unresolved constant names: {}", unresolved);
    }
    return new NameResolution(resolved, unresolved);
  }

  private Optional<Long> itemIdFor(String name) {
    Map<String, ItemConstant> items = constantsManagers.getItemConstantMap();

    ItemConstant byKey = items.get(name.toLowerCase());
    if (byKey != null && byKey.getId() != null) {
      return Optional.of(byKey.getId());
    }
    return items.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().equalsIgnoreCase(name)
                    || (entry.getValue().getDname() != null
                        && entry.getValue().getDname().equalsIgnoreCase(name)))
        .map(entry -> entry.getValue().getId())
        .filter(Objects::nonNull)
        .findFirst();
  }

  private Optional<Long> heroIdFor(String name) {
    return constantsManagers.getHeroConstantMap().values().stream()
        .filter(
            hero ->
                (hero.getName() != null && hero.getName().equalsIgnoreCase(name))
                    || (hero.getLocalized_name() != null
                        && hero.getLocalized_name().equalsIgnoreCase(name)))
        .map(HeroConstant::getId)
        .filter(Objects::nonNull)
        .map(Long::valueOf)
        .findFirst();
  }

  private Optional<Long> abilityIdFor(String name) {
    return constantsManagers.getAbilityConstantMap().values().stream()
        .filter(ability -> ability.getName() != null && ability.getName().equalsIgnoreCase(name))
        .map(AbilityIdsConstant::getId)
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * Every string a user might plausibly type for an item, mapped to the name to show them back.
   *
   * <p>Both the short name ({@code blink}) and the display name ({@code Blink Dagger}) are
   * candidates, because a typo of the short name is otherwise unreachable: {@code blnk} is edit
   * distance 8 from {@code Blink Dagger} but 1 from {@code blink}.
   */
  private Map<String, String> itemSuggestionCandidates() {
    Map<String, String> candidates = new LinkedHashMap<>();
    constantsManagers
        .getItemConstantMap()
        .forEach(
            (key, item) -> {
              String display = item.getDname() != null ? item.getDname() : key;
              candidates.put(key, display);
              if (item.getDname() != null) {
                candidates.put(item.getDname(), item.getDname());
              }
            });
    return candidates;
  }

  private Map<String, String> heroSuggestionCandidates() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (HeroConstant hero : constantsManagers.getHeroConstantMap().values()) {
      String display = hero.getLocalized_name();
      if (display == null) {
        continue;
      }
      candidates.put(display, display);
      if (hero.getName() != null) {
        candidates.put(hero.getName(), display);
      }
    }
    return candidates;
  }

  private Map<String, String> abilitySuggestionCandidates() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (AbilityIdsConstant ability : constantsManagers.getAbilityConstantMap().values()) {
      if (ability.getName() != null) {
        candidates.put(ability.getName(), ability.getName());
      }
    }
    return candidates;
  }

  /**
   * The display name of the nearest candidate, or empty when nothing is within {@link
   * #MAX_SUGGESTION_DISTANCE}.
   *
   * @param unknown what the user typed
   * @param candidates matchable string to the display name it should suggest
   * @return the suggested display name, if one is close enough
   */
  private Optional<String> closest(String unknown, Map<String, String> candidates) {
    if (unknown == null || unknown.isBlank()) {
      return Optional.empty();
    }
    String needle = unknown.trim().toLowerCase();

    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (Map.Entry<String, String> candidate : candidates.entrySet()) {
      int distance = TextSimilarity.editDistance(needle, candidate.getKey().toLowerCase());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate.getValue();
      }
    }
    return bestDistance <= MAX_SUGGESTION_DISTANCE ? Optional.ofNullable(best) : Optional.empty();
  }
}
