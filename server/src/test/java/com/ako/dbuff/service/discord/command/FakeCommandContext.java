package com.ako.dbuff.service.discord.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * In-memory {@link CommandContext} for handler tests. Captures everything a handler says instead of
 * sending it to Discord.
 */
@Getter
public class FakeCommandContext implements CommandContext {

  private final Map<String, String> options;
  private final String invokerId;
  private final String channelId;
  private final String parentChannelId;
  private final String guildId;
  private final boolean insideThread;
  private final String threadName;
  private final byte[] attachment;
  private final RuntimeException attachmentFailure;

  private final List<String> posts = new ArrayList<>();
  private final List<MessageEmbed> embeds = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private final List<String> ephemeralReplies = new ArrayList<>();

  private String acknowledgeSummary;
  private String acknowledgeThreadName;

  private FakeCommandContext(Builder builder) {
    this.options = builder.options;
    this.invokerId = builder.invokerId;
    this.channelId = builder.channelId;
    this.parentChannelId = builder.parentChannelId;
    this.guildId = builder.guildId;
    this.insideThread = builder.insideThread;
    this.threadName = builder.threadName;
    this.attachment = builder.attachment;
    this.attachmentFailure = builder.attachmentFailure;
  }

  public static Builder builder() {
    return new Builder();
  }

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
    return Optional.ofNullable(options.get(name));
  }

  @Override
  public Optional<byte[]> downloadAttachment(String name) {
    if (attachmentFailure != null) {
      throw attachmentFailure;
    }
    return Optional.ofNullable(attachment);
  }

  @Override
  public Optional<String> getGuildId() {
    return Optional.ofNullable(guildId);
  }

  @Override
  public Optional<String> getThreadName() {
    return Optional.ofNullable(threadName);
  }

  @Override
  public AsyncReply acknowledge(String summary, String threadName) {
    this.acknowledgeSummary = summary;
    this.acknowledgeThreadName = threadName;
    return new AsyncReply() {
      @Override
      public void post(String message) {
        posts.add(message);
      }

      @Override
      public void postEmbed(MessageEmbed embed) {
        embeds.add(embed);
      }

      @Override
      public void fail(String message) {
        failures.add(message);
      }
    };
  }

  @Override
  public void replyEphemeral(String message) {
    ephemeralReplies.add(message);
  }

  /** Fluent builder. All fields have sensible defaults so tests set only what they care about. */
  public static class Builder {
    private final Map<String, String> options = new HashMap<>();
    private String invokerId = "invoker-1";
    private String channelId = "channel-1";
    private String parentChannelId = "channel-1";
    private String guildId = "guild-1";
    private boolean insideThread = false;
    private String threadName = null;
    private byte[] attachment = null;
    private RuntimeException attachmentFailure = null;

    public Builder option(String name, String value) {
      options.put(name, value);
      return this;
    }

    public Builder invokerId(String invokerId) {
      this.invokerId = invokerId;
      return this;
    }

    public Builder channelId(String channelId) {
      this.channelId = channelId;
      return this;
    }

    public Builder parentChannelId(String parentChannelId) {
      this.parentChannelId = parentChannelId;
      return this;
    }

    public Builder guildId(String guildId) {
      this.guildId = guildId;
      return this;
    }

    public Builder insideThread(boolean insideThread) {
      this.insideThread = insideThread;
      return this;
    }

    /** Marks the context as inside a thread with the given name. */
    public Builder threadName(String threadName) {
      this.threadName = threadName;
      this.insideThread = true;
      return this;
    }

    /** Makes {@code downloadAttachment} return these bytes for any option name. */
    public Builder attachment(byte[] attachment) {
      this.attachment = attachment;
      return this;
    }

    /** Makes {@code downloadAttachment} throw, as a failed download does. */
    public Builder attachmentFailure(RuntimeException attachmentFailure) {
      this.attachmentFailure = attachmentFailure;
      return this;
    }

    public FakeCommandContext build() {
      return new FakeCommandContext(this);
    }
  }
}
