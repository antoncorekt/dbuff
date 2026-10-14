package com.ako.dbuff.service.discord.command.adapter;

import java.util.Optional;

/**
 * Splits a legacy prefixed text command into alias, subcommand, and remaining arguments.
 *
 * <p>Whether the second token is a subcommand or an argument depends on the command, so both are
 * exposed and {@link TextCommandAdapter} decides using the registry.
 */
public final class TextCommandParser {

  private TextCommandParser() {}

  /**
   * A parsed text command.
   *
   * @param alias the command alias, lower-cased, without the prefix
   * @param subcommand the second token, or null when there was only one token
   * @param arguments everything after the subcommand, never null
   */
  public record ParsedCommand(String alias, String subcommand, String arguments) {}

  /**
   * Parses raw message content.
   *
   * @param prefix the configured text-command prefix
   * @param rawContent the message content
   * @return the parsed command, or empty when the content does not start with the prefix
   */
  public static Optional<ParsedCommand> parse(String prefix, String rawContent) {
    if (rawContent == null) {
      return Optional.empty();
    }
    String content = rawContent.trim();
    if (!content.startsWith(prefix)) {
      return Optional.empty();
    }

    String body = content.substring(prefix.length()).trim();
    if (body.isEmpty()) {
      return Optional.empty();
    }

    String[] parts = body.split("\\s+", 3);
    String alias = parts[0].toLowerCase();
    String subcommand = parts.length > 1 ? parts[1] : null;
    String arguments = parts.length > 2 ? parts[2].trim() : "";

    return Optional.of(new ParsedCommand(alias, subcommand, arguments));
  }
}
