package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Read-only {@link CommandContext} for autocomplete, where sibling option values are available but
 * replying is not.
 *
 * <p>The reply methods throw rather than silently doing nothing, because calling them from a
 * provider is a programming error and should fail loudly in tests.
 */
@RequiredArgsConstructor
public class AutocompleteCommandContext implements CommandContext {

  private final CommandAutoCompleteInteractionEvent event;

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
    return event.getChannel().getId();
  }

  @Override
  public Optional<String> getGuildId() {
    return event.getGuild() == null ? Optional.empty() : Optional.of(event.getGuild().getId());
  }

  @Override
  public boolean isInsideThread() {
    return false;
  }

  @Override
  public Optional<String> getThreadName() {
    return Optional.empty();
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    throw new UnsupportedOperationException("Cannot acknowledge an autocomplete interaction");
  }

  @Override
  public void replyEphemeral(String message) {
    throw new UnsupportedOperationException("Cannot reply to an autocomplete interaction");
  }
}
