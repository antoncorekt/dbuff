package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * The tracked-player list, for the {@code /dbuff} subcommands that operate on players already in
 * the focus group.
 *
 * <p>Registered against a specific subcommand so it does not shadow {@link
 * OpenDotaPlayerAutocomplete}, which serves {@code /dbuff players add} and searches all of
 * OpenDota. One instance per subcommand, because the adapter keys providers by
 * command-plus-subcommand-plus-option.
 */
@Component
@RequiredArgsConstructor
public class TrackedPlayerForDbuffAutocomplete implements AutocompleteProvider {

  private final TrackedPlayerAutocomplete delegate;

  @Override
  public String getOptionName() {
    return "player";
  }

  @Override
  public String getCommandName() {
    return "dbuff";
  }

  @Override
  public String getSubcommandName() {
    return "remove";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    // Single-valued here: /dbuff players remove and link act on one player at a time.
    return ChoiceAccumulator.single(currentInput, delegate.trackedPlayers(context));
  }
}
