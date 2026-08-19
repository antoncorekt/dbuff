package com.ako.dbuff.service.discord.command;

import java.util.List;
import java.util.Optional;

/**
 * Everything a command handler needs, normalized across the slash and text surfaces.
 *
 * <p>The two-phase reply model is deliberate. Discord discards an interaction that is not
 * acknowledged within three seconds, and every interesting command here (statistics aggregations,
 * OCR, OpenDota fetches) can exceed that. So handlers validate first, {@link #acknowledge} second,
 * and do the slow work third — writing results into the {@link AsyncReply} they got back.
 *
 * <p>Validation failures therefore use {@link #replyEphemeral}, which must be called
 * <em>before</em> acknowledging: Discord does not allow a thread on an ephemeral message.
 */
public interface CommandContext {

  /** Raw option value, or null when absent. */
  String getOption(String name);

  /**
   * A comma-separated option split into trimmed, non-blank entries. Empty list when absent.
   *
   * <p>This is how list-valued options arrive, because Discord options are single-valued.
   */
  List<String> getOptionAsList(String name);

  /** Integer option, falling back to {@code defaultValue} when absent or unparseable. */
  int getOptionAsInt(String name, int defaultValue);

  /** Discord user snowflake for a user-typed option, or empty when absent. */
  Optional<String> getOptionAsUserId(String name);

  /** Snowflake of the user who invoked the command. */
  String getInvokerId();

  /** Channel the command was invoked in. For a thread, the thread's own ID. */
  String getChannelId();

  /** Parent text channel ID — the thread's parent when inside a thread, else the channel itself. */
  String getParentChannelId();

  /** Guild ID, or empty in a direct message. */
  Optional<String> getGuildId();

  /** True when invoked inside a thread, where a new thread cannot be created. */
  boolean isInsideThread();

  /**
   * Thread name when invoked inside a thread, else empty. Used by {@code /match} to find its ID.
   */
  Optional<String> getThreadName();

  /**
   * Acknowledges the command visibly and returns the sink for its results.
   *
   * @param summary short public message confirming the command was accepted
   * @param threadName name for the created thread; truncated to Discord's 100-character limit
   * @return the sink to write results into
   */
  AsyncReply acknowledge(String summary, String threadName);

  /**
   * Replies privately to the invoker without creating a thread. For validation failures only — must
   * be called before {@link #acknowledge}.
   */
  void replyEphemeral(String message);
}
