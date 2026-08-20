package com.ako.dbuff.service.discord.command.autocomplete;

import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Supplies autocomplete choices for one option of one command.
 *
 * <p>Implementations must never throw and must return within Discord's three-second autocomplete
 * budget. A provider that throws leaves the user with a silently broken picker and no explanation,
 * so the adapter catches everything — but providers should not rely on that.
 */
public interface AutocompleteProvider {

  /** The option name this provider serves, e.g. {@code items}. */
  String getOptionName();

  /** The command this provider belongs to, e.g. {@code stats}. */
  String getCommandName();

  /**
   * The subcommand this provider is restricted to, or null to serve the option across every
   * subcommand.
   *
   * <p>Needed because one option name can mean different things per subcommand: {@code /dbuff
   * players add player:} searches all of OpenDota, while {@code /dbuff players remove player:} must
   * offer only the players already tracked. The adapter prefers a subcommand-specific provider over
   * a general one.
   *
   * @return the subcommand name, or null for any
   */
  default String getSubcommandName() {
    return null;
  }

  /**
   * Choices for the current partial input.
   *
   * @param currentInput what the user has typed into this option so far
   * @param context the in-flight command context, for providers that need channel or sibling-option
   *     scope
   * @return at most 25 choices; empty rather than an exception on failure
   */
  List<Command.Choice> getChoices(String currentInput, CommandContext context);
}
