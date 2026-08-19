package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** {@link CommandContext} over a slash-command interaction. */
@RequiredArgsConstructor
public class InteractionCommandContext implements CommandContext {

  private final SlashCommandInteractionEvent event;

  @Override
  public String getOption(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? null : option.getAsString();
  }

  @Override
  public List<String> getOptionAsList(String name) {
    String raw = getOption(name);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public int getOptionAsInt(String name, int defaultValue) {
    OptionMapping option = event.getOption(name);
    if (option == null) {
      return defaultValue;
    }
    try {
      return option.getAsInt();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  @Override
  public Optional<String> getOptionAsUserId(String name) {
    OptionMapping option = event.getOption(name);
    return option == null ? Optional.empty() : Optional.of(option.getAsUser().getId());
  }

  @Override
  public String getInvokerId() {
    return event.getUser().getId();
  }

  @Override
  public String getChannelId() {
    return event.getChannel().getId();
  }

  @Override
  public String getParentChannelId() {
    return event.getChannel() instanceof ThreadChannel thread
        ? thread.getParentChannel().getId()
        : event.getChannel().getId();
  }

  @Override
  public Optional<String> getGuildId() {
    return event.getGuild() == null ? Optional.empty() : Optional.of(event.getGuild().getId());
  }

  @Override
  public boolean isInsideThread() {
    return event.getChannel() instanceof ThreadChannel;
  }

  @Override
  public Optional<String> getThreadName() {
    return event.getChannel() instanceof ThreadChannel thread
        ? Optional.of(thread.getName())
        : Optional.empty();
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    // Reply first: this is what satisfies Discord's 3-second window. Everything after
    // it can take as long as it needs.
    event.reply(summary).complete();

    if (isInsideThread()) {
      // Threads cannot nest, so results go into the current thread.
      return new ThreadAsyncReply(event.getChannel());
    }

    Message original = event.getHook().retrieveOriginal().complete();
    ThreadChannel thread =
        original
            .createThreadChannel(DiscordNames.sanitizeThreadName(threadName))
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
            .complete();

    return new ThreadAsyncReply(thread);
  }

  @Override
  public void replyEphemeral(String message) {
    event.reply(message).setEphemeral(true).complete();
  }
}
