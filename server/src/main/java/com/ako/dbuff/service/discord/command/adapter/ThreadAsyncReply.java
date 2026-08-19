package com.ako.dbuff.service.discord.command.adapter;

import com.ako.dbuff.service.discord.command.AsyncReply;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Posts results into an already-resolved channel — a freshly created thread, or the thread the
 * command was invoked in.
 *
 * <p>Uses {@code complete()} rather than {@code queue()} so ordering is preserved: a handler
 * looping over players sees its embeds appear in order. Safe because this only ever runs on a
 * virtual thread, never on the JDA gateway thread.
 */
@Slf4j
@RequiredArgsConstructor
public class ThreadAsyncReply implements AsyncReply {

  /** Discord rejects message content longer than this. */
  static final int MAX_MESSAGE_LENGTH = 2000;

  private final MessageChannel channel;

  @Override
  public void post(String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    for (String chunk : splitToLimit(message)) {
      channel.sendMessage(chunk).complete();
    }
  }

  @Override
  public void postEmbed(MessageEmbed embed) {
    channel.sendMessageEmbeds(embed).complete();
  }

  @Override
  public void fail(String message) {
    log.warn("Command failed in channel {}: {}", channel.getId(), message);
    channel.sendMessage("❌ " + truncate(message)).complete();
  }

  /** Splits on newlines where possible, hard-splitting only when a single line is too long. */
  static List<String> splitToLimit(String message) {
    if (message.length() <= MAX_MESSAGE_LENGTH) {
      return List.of(message);
    }
    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (String rawLine : message.split("\n", -1)) {
      String line = rawLine;
      while (line.length() > MAX_MESSAGE_LENGTH) {
        if (current.length() > 0) {
          chunks.add(current.toString());
          current.setLength(0);
        }
        chunks.add(line.substring(0, MAX_MESSAGE_LENGTH));
        line = line.substring(MAX_MESSAGE_LENGTH);
      }
      if (current.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
        chunks.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) {
        current.append('\n');
      }
      current.append(line);
    }
    if (current.length() > 0) {
      chunks.add(current.toString());
    }
    return chunks;
  }

  private static String truncate(String message) {
    String safe = message == null ? "Unknown error" : message;
    return safe.length() <= MAX_MESSAGE_LENGTH - 2
        ? safe
        : safe.substring(0, MAX_MESSAGE_LENGTH - 5) + "…";
  }
}
