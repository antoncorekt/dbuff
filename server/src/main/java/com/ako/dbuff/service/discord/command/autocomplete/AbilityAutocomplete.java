package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Ability autocomplete.
 *
 * <p>List-valued, and narrowed to the selected hero's abilities when the sibling {@code hero:}
 * option is already filled in — without narrowing the picker offers well over a thousand entries
 * and the 25-choice cap makes it useless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "skills";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, displayToValue(context.getOption("hero")));
    } catch (Exception e) {
      log.debug("Ability autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue(String heroName) {
    Set<String> heroAbilities = abilitiesOfHero(heroName);

    Map<String, String> candidates = new LinkedHashMap<>();
    for (AbilityIdsConstant ability : constantsManagers.getAbilityConstantMap().values()) {
      String name = ability.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      if (heroAbilities != null && !heroAbilities.contains(name)) {
        continue;
      }
      candidates.put(name, name);
    }
    return candidates;
  }

  /**
   * The internal ability names belonging to {@code heroName}, or null when no narrowing should
   * apply.
   *
   * <p>Returns null rather than an empty set on any failure to resolve the hero. An empty set would
   * make the picker look broken; null degrades to offering every ability, which is merely
   * unhelpful.
   */
  private Set<String> abilitiesOfHero(String heroName) {
    if (heroName == null || heroName.isBlank()) {
      return null;
    }

    Optional<String> internalName = internalHeroName(heroName);
    if (internalName.isEmpty()) {
      return null;
    }

    HeroesAbilityConstant heroAbilities =
        constantsManagers.getHeroAbilitiesMap().get(internalName.get());
    if (heroAbilities == null
        || heroAbilities.getAbilities() == null
        || heroAbilities.getAbilities().isEmpty()) {
      return null;
    }
    return new LinkedHashSet<>(heroAbilities.getAbilities());
  }

  /**
   * Maps whatever the user has in the hero option to the internal hero name that keys the
   * hero-abilities map. {@code HeroAutocomplete} submits the localized name, but accept the
   * internal name too since a user may type it.
   */
  private Optional<String> internalHeroName(String heroName) {
    return constantsManagers.getHeroConstantMap().values().stream()
        .filter(
            hero ->
                (hero.getLocalized_name() != null
                        && hero.getLocalized_name().equalsIgnoreCase(heroName))
                    || (hero.getName() != null && hero.getName().equalsIgnoreCase(heroName)))
        .map(HeroConstant::getName)
        .filter(name -> name != null && !name.isBlank())
        .findFirst();
  }
}
