package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.discord.command.autocomplete.AutocompleteProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

/**
 * Routes slash-command interactions and autocomplete requests to {@link DbuffCommand} handlers, and
 * registers the command definitions with every guild the bot is in on startup.
 *
 * <p>Guild-scoped registration rather than global: it propagates immediately instead of taking up
 * to an hour, which matters when iterating.
 */
@Slf4j
@Component
public class SlashCommandAdapter extends ListenerAdapter {

  private final CommandRegistry registry;
  private final Map<String, AutocompleteProvider> providersByKey;

  public SlashCommandAdapter(CommandRegistry registry, List<AutocompleteProvider> providers) {
    this.registry = registry;
    this.providersByKey =
        providers.stream()
            .collect(
                Collectors.toMap(
                    provider ->
                        providerKey(
                            provider.getCommandName(),
                            provider.getSubcommandName(),
                            provider.getOptionName()),
                    Function.identity(),
                    (first, second) -> first));
  }

  @Override
  public void onReady(ReadyEvent event) {
    for (Guild guild : event.getJDA().getGuilds()) {
      guild
          .updateCommands()
          .addCommands(registry.getDefinitions())
          .queue(
              success ->
                  log.info(
                      "Registered {} slash commands with guild {}",
                      registry.getDefinitions().size(),
                      guild.getId()),
              error ->
                  log.error(
                      "Failed to register slash commands with guild {}: {}",
                      guild.getId(),
                      error.getMessage(),
                      error));
    }
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    Optional<DbuffCommand> command = registry.findByName(event.getName());
    if (command.isEmpty()) {
      // Registered with Discord but no handler bean — a wiring bug, not user error.
      log.error("No handler for slash command /{}", event.getName());
      event.reply("This command is not available right now.").setEphemeral(true).queue();
      return;
    }

    CommandContext context = new InteractionCommandContext(event);
    String subcommand = event.getSubcommandName();

    Thread.startVirtualThread(
        () -> {
          try {
            command.get().execute(subcommand, context);
          } catch (Exception e) {
            log.error(
                "Slash command /{} {} failed: {}", event.getName(), subcommand, e.getMessage(), e);
            reportFailure(event, e);
          }
        });
  }

  @Override
  public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
    String option = event.getFocusedOption().getName();
    AutocompleteProvider provider =
        resolveProvider(event.getName(), event.getSubcommandName(), option);
    if (provider == null) {
      event.replyChoices(List.of()).queue();
      return;
    }

    List<Command.Choice> choices;
    try {
      choices =
          provider.getChoices(
              event.getFocusedOption().getValue(), new AutocompleteCommandContext(event));
    } catch (Exception e) {
      // A broken picker with no explanation is worse than an empty one.
      log.debug("Autocomplete for {}:{} failed: {}", event.getName(), option, e.getMessage());
      choices = List.of();
    }
    event.replyChoices(choices).queue();
  }

  /**
   * Best-effort error reporting. The interaction may already be acknowledged, in which case the
   * hook is the only way to reach the user; if it is not, a fresh ephemeral reply works.
   */
  private void reportFailure(SlashCommandInteractionEvent event, Exception cause) {
    String message = "❌ " + (cause.getMessage() == null ? "Unexpected error" : cause.getMessage());
    try {
      if (event.isAcknowledged()) {
        event.getHook().sendMessage(message).setEphemeral(true).queue();
      } else {
        event.reply(message).setEphemeral(true).queue();
      }
    } catch (Exception e) {
      log.error("Could not report failure to the user: {}", e.getMessage());
    }
  }

  /**
   * Most-specific-first lookup: a provider registered for this exact subcommand wins over one
   * registered for the whole command. That is what lets {@code /dbuff players add player:} search
   * OpenDota while {@code /dbuff players remove player:} offers only tracked players.
   */
  private AutocompleteProvider resolveProvider(
      String commandName, String subcommandName, String optionName) {
    if (subcommandName != null) {
      AutocompleteProvider specific =
          providersByKey.get(providerKey(commandName, subcommandName, optionName));
      if (specific != null) {
        return specific;
      }
    }
    return providersByKey.get(providerKey(commandName, null, optionName));
  }

  private static String providerKey(String commandName, String subcommandName, String optionName) {
    return commandName.toLowerCase()
        + ":"
        + (subcommandName == null ? "*" : subcommandName.toLowerCase())
        + ":"
        + optionName.toLowerCase();
  }
}
