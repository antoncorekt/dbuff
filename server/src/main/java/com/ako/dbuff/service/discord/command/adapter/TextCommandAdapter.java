package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Routes legacy {@code !} text commands to the same {@link DbuffCommand} handlers as the slash
 * surface.
 *
 * <p>Kept so existing muscle memory ({@code !vs}, {@code !dbuf status}, {@code !rerun}) keeps
 * working. Each command translates its own argument string into option values, so handlers see no
 * difference between the two surfaces.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextCommandAdapter extends ListenerAdapter {

  private final CommandRegistry registry;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) {
      return;
    }

    Optional<TextCommandParser.ParsedCommand> parsed =
        TextCommandParser.parse(event.getMessage().getContentRaw());
    if (parsed.isEmpty()) {
      return;
    }
    TextCommandParser.ParsedCommand parsedCommand = parsed.get();

    Optional<DbuffCommand> resolved = registry.findByTextAlias(parsedCommand.alias());
    if (resolved.isEmpty()) {
      // Silently ignored: the channel may carry unrelated `!` messages from other bots,
      // and complaining about each one would be noise.
      return;
    }
    DbuffCommand command = resolved.get();

    String subcommand =
        command.resolveTextSubcommand(parsedCommand.alias(), parsedCommand.subcommand());

    if (registry.isUnknownSubcommand(command, subcommand)) {
      replyUnknownSubcommand(event.getMessage(), command, subcommand);
      return;
    }

    Map<String, String> options =
        command.parseTextArguments(
            parsedCommand.alias(), parsedCommand.subcommand(), parsedCommand.arguments());
    CommandContext context = new MessageCommandContext(event.getMessage(), options);

    Thread.startVirtualThread(
        () -> {
          try {
            command.execute(subcommand, context);
          } catch (Exception e) {
            log.error(
                "Text command !{} {} failed: {}",
                parsedCommand.alias(),
                subcommand,
                e.getMessage(),
                e);
            event
                .getChannel()
                .sendMessage("❌ " + (e.getMessage() == null ? "Unexpected error" : e.getMessage()))
                .queue();
          }
        });
  }

  /**
   * Replaces the old silent fall-through to help, which made {@code !dbuf add-player 123} look like
   * it had partly worked.
   */
  private void replyUnknownSubcommand(Message message, DbuffCommand command, String subcommand) {
    String suggestion =
        registry
            .suggestSubcommand(command.getName(), subcommand)
            .map(name -> " Did you mean `" + name + "`?")
            .orElse(" Try `/" + command.getName() + " help`.");

    String label = subcommand == null ? "(none)" : subcommand;
    message
        .getChannel()
        .sendMessage(
            "❌ Unknown `" + command.getName() + "` subcommand `" + label + "`." + suggestion)
        .queue();
  }
}
