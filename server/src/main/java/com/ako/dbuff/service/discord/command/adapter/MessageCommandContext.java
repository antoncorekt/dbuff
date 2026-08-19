package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * {@link CommandContext} over a legacy {@code !} text message.
 *
 * <p>Options come from a map the command built by parsing its argument string, so handlers cannot
 * tell which surface invoked them. Threads are created on the user's own message, matching what the
 * text listeners this replaced did.
 */
@RequiredArgsConstructor
public class MessageCommandContext implements CommandContext {

  private final Message sourceMessage;
  private final Map<String, String> options;

  @Override
  public String getOption(String name) {
    return options.get(name);
  }

  @Override
  public List<String> getOptionAsList(String name) {
    String raw = options.get(name);
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public int getOptionAsInt(String name, int defaultValue) {
    String raw = options.get(name);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public Optional<String> getOptionAsUserId(String name) {
    // Text commands carry mentions rather than resolved user options.
    String raw = options.get(name);
    if (raw != null && !raw.isBlank()) {
      return Optional.of(raw.replaceAll("[^0-9]", "")).filter(id -> !id.isEmpty());
    }
    return sourceMessage.getMentions().getUsers().isEmpty()
        ? Optional.empty()
        : Optional.of(sourceMessage.getMentions().getUsers().get(0).getId());
  }

  @Override
  public String getInvokerId() {
    return sourceMessage.getAuthor().getId();
  }

  @Override
  public String getChannelId() {
    return sourceMessage.getChannel().getId();
  }

  @Override
  public String getParentChannelId() {
    return sourceMessage.getChannel() instanceof ThreadChannel thread
        ? thread.getParentChannel().getId()
        : sourceMessage.getChannel().getId();
  }

  @Override
  public Optional<String> getGuildId() {
    try {
      return Optional.of(sourceMessage.getGuild().getId());
    } catch (IllegalStateException e) {
      // Direct message: JDA throws rather than returning null.
      return Optional.empty();
    }
  }

  @Override
  public boolean isInsideThread() {
    return sourceMessage.getChannel() instanceof ThreadChannel;
  }

  @Override
  public Optional<String> getThreadName() {
    return sourceMessage.getChannel() instanceof ThreadChannel thread
        ? Optional.of(thread.getName())
        : Optional.empty();
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    MessageChannel channel = sourceMessage.getChannel();
    channel.sendMessage(summary).complete();

    if (isInsideThread()) {
      return new ThreadAsyncReply(channel);
    }

    ThreadChannel thread =
        sourceMessage
            .createThreadChannel(DiscordNames.sanitizeThreadName(threadName))
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
            .complete();

    return new ThreadAsyncReply(thread);
  }

  @Override
  public void replyEphemeral(String text) {
    // Text commands have no ephemeral equivalent, so mention the invoker to keep the
    // reply obviously theirs.
    sourceMessage
        .getChannel()
        .sendMessage("<@" + sourceMessage.getAuthor().getId() + "> " + text)
        .complete();
  }
}
