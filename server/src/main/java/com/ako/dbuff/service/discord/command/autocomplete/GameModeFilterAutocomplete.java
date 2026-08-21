package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * The {@code game_mode:} statistics filter on {@code /stats} and {@code /hero}.
 *
 * <p>Autocomplete rather than the fixed choice list Discord offers, because the filter is
 * list-valued — "Ability Draft and All Pick" is a reasonable question and a static choice option
 * can only ever submit one value.
 *
 * <p>Distinct from {@link GameModeAutocomplete}, which serves {@code /dbuff mode:} and submits a
 * numeric ID because that option writes the instance configuration. This one submits readable names
 * so that a user editing the option box by hand can still tell what it says.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameModeFilterAutocomplete implements AutocompleteProvider {

  private final GameModeResolver gameModeResolver;

  @Override
  public String getOptionName() {
    return "game_mode";
  }

  @Override
  public String getCommandName() {
    return "stats";
  }

  @Override
  public Set<String> getCommandNames() {
    return Set.of("stats", "hero");
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    try {
      return ChoiceAccumulator.accumulate(currentInput, gameModeResolver.displayToSubmittedValue());
    } catch (Exception e) {
      log.debug("Game mode filter autocomplete failed for '{}': {}", currentInput, e.getMessage());
      return List.of();
    }
  }
}
