package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.CommandRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Component;

/**
 * Publishes the slash command definitions to a guild.
 *
 * <p>Guild-scoped rather than global: it propagates immediately instead of taking up to an hour.
 * Shared by startup and by joining a new server, so a freshly invited bot answers at once instead
 * of waiting for the next restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuildCommandRegistrar {

  private final CommandRegistry registry;

  public void register(Guild guild) {
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
