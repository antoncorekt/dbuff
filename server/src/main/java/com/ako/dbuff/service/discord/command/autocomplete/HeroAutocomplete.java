package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Hero autocomplete.
 *
 * <p>Single-valued: a game has exactly one hero, so a list could only mean OR, which would clash
 * with the conjunctive meaning of the item and skill lists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeroAutocomplete implements AutocompleteProvider {

  private final ConstantsManagers constantsManagers;

  @Override
  public String getOptionName() {
    return "hero";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  /** {@code /hero hero:} asks the same question, from the same dictionary. */
  @Override
  public Set<String> getCommandNames() {
    return Set.of("stats", "hero");
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.single(currentInput, displayToValue());
    } catch (Exception e) {
      log.debug("Hero autocomplete failed for input '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }

  private Map<String, String> displayToValue() {
    Map<String, String> candidates = new LinkedHashMap<>();
    for (HeroConstant hero : constantsManagers.getHeroConstantMap().values()) {
      String display = hero.getLocalized_name();
      if (display != null && !display.isBlank()) {
        // Submits the localized name, which ConstantNameResolver accepts and which stays legible if
        // the user edits the value by hand.
        candidates.put(display, display);
      }
    }
    return candidates;
  }
}
