package com.ako.dbuff.service.discord.command;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * One top-level Discord command, e.g. {@code /stats}, including all its subcommands.
 *
 * <p>Implementations are Spring beans collected by {@link CommandRegistry}. They must not touch JDA
 * directly — everything goes through {@link CommandContext}, which is what allows them to be tested
 * without a Discord connection.
 */
public interface DbuffCommand {

  /** Root command name without the leading slash, e.g. {@code stats}. */
  String getName();

  /** Full JDA definition, used both for registration and for generating help. */
  SlashCommandData getDefinition();

  /**
   * Text-command aliases that route here, without the {@code !} prefix, e.g. {@code vs}. Empty when
   * the command is slash-only.
   *
   * @return the aliases
   */
  default List<String> getTextAliases() {
    return List.of();
  }

  /**
   * Maps a legacy text invocation onto slash option values.
   *
   * <p>Only commands with text aliases need to override this. The returned map is keyed by option
   * name, exactly as the slash surface would supply it, so handlers see no difference between the
   * two surfaces.
   *
   * @param alias the alias that was typed, lower-cased and without the {@code !}
   * @param subcommand the second token, or null when there was only one
   * @param arguments everything after the subcommand
   * @return option name to value
   */
  default Map<String, String> parseTextArguments(
      String alias, String subcommand, String arguments) {
    return Map.of();
  }

  /**
   * Resolves which subcommand a legacy text invocation targets.
   *
   * <p>Defaults to the parsed second token. Commands whose aliases <em>are</em> subcommands —
   * {@code !rerun} meaning {@code /match rerun} — override this.
   *
   * @param alias the alias that was typed
   * @param parsedSubcommand the second token, or null
   * @return the subcommand to execute, or null for a root-level invocation
   */
  default String resolveTextSubcommand(String alias, String parsedSubcommand) {
    return parsedSubcommand;
  }

  /**
   * Runs the command.
   *
   * @param subcommand the invoked subcommand name, or null for a root-level invocation
   * @param context normalized arguments and reply sinks
   */
  void execute(String subcommand, CommandContext context);
}
