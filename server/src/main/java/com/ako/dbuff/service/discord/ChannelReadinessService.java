package com.ako.dbuff.service.discord;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Checks that the bot can actually report into a channel, so registration fails loudly at the
 * moment of setup instead of silently on the first match.
 */
@Service
public class ChannelReadinessService {

  private final JDA jda;

  // Lazy because JDA is built from the listeners, which reach the command beans that use this.
  public ChannelReadinessService(@Lazy JDA jda) {
    this.jda = jda;
  }

  /**
   * @param channelId the channel an instance is, or is about to be, registered against
   * @return human-readable problems; empty when the bot can report there
   */
  public List<String> findProblems(String channelId) {
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      return List.of(
          "The bot cannot see this channel, or it is not a text channel (forums and voice"
              + " channels are not supported).");
    }
    List<String> missing =
        BotPermissions.REQUIRED.stream()
            .filter(
                permission ->
                    !channel.getGuild().getSelfMember().hasPermission(channel, permission))
            .map(Permission::getName)
            .toList();
    return missing.isEmpty()
        ? List.of()
        : List.of("The bot is missing permissions in this channel: " + String.join(", ", missing));
  }
}
