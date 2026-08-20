package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * The tracked-player list for {@code /dbuff link}, which attaches a Discord account to a player
 * that is already in the focus group.
 */
@Component
@RequiredArgsConstructor
public class LinkPlayerAutocomplete implements AutocompleteProvider {

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
    return "link";
  }

  @Override
  public List<Command.Choice> getChoices(String currentInput, CommandContext context) {
    return ChoiceAccumulator.single(currentInput, delegate.trackedPlayers(context));
  }
}
