package com.ako.dbuff.service.discord.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for which commands exist.
 *
 * <p>Both adapters dispatch through this, JDA registration reads its definitions, and help text is
 * generated from it — so help cannot drift from the actual command set. The listeners this replaced
 * each kept a hand-written help embed, which is how one of them ended up dead and unnoticed:
 * nothing tied the advertised commands to the implemented ones.
 */
@Component
public class CommandRegistry {

  private static final int MAX_SUGGESTION_DISTANCE = 4;
  private static final int EMBED_COLOR = 0x00AE86;

  private final List<DbuffCommand> commands;
  private final Map<String, DbuffCommand> byName = new LinkedHashMap<>();
  private final Map<String, DbuffCommand> byTextAlias = new LinkedHashMap<>();

  public CommandRegistry(List<DbuffCommand> commands) {
    this.commands = List.copyOf(commands);
    for (DbuffCommand command : commands) {
      byName.put(command.getName().toLowerCase(), command);
      byTextAlias.put(command.getName().toLowerCase(), command);
      for (String alias : command.getTextAliases()) {
        byTextAlias.put(alias.toLowerCase(), command);
      }
    }
  }

  public Optional<DbuffCommand> findByName(String name) {
    return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase()));
  }

  public Optional<DbuffCommand> findByTextAlias(String alias) {
    return alias == null
        ? Optional.empty()
        : Optional.ofNullable(byTextAlias.get(alias.toLowerCase()));
  }

  /**
   * All JDA definitions, for guild registration.
   *
   * @return one definition per registered command
   */
  public List<SlashCommandData> getDefinitions() {
    return commands.stream().map(DbuffCommand::getDefinition).toList();
  }

  /**
   * Nearest known command name or alias, for "did you mean" on a mistyped text command.
   *
   * @param input the token that did not resolve
   * @return the nearest known name, if close enough
   */
  public Optional<String> suggestCommand(String input) {
    return TextSimilarity.closest(input, byTextAlias.keySet(), MAX_SUGGESTION_DISTANCE);
  }

  /**
   * Nearest subcommand of {@code commandName}, or empty if it has none or does not exist.
   *
   * @param commandName the resolved root command
   * @param input the subcommand token that did not resolve
   * @return the nearest subcommand name, if close enough
   */
  public Optional<String> suggestSubcommand(String commandName, String input) {
    return findByName(commandName)
        .map(command -> subcommandNames(command.getDefinition()))
        .filter(names -> !names.isEmpty())
        .flatMap(names -> TextSimilarity.closest(input, names, MAX_SUGGESTION_DISTANCE));
  }

  /**
   * True when the command has subcommands and {@code subcommand} is not one of them.
   *
   * @param command the resolved command
   * @param subcommand the token to check, may be null
   * @return whether the token is an unknown subcommand
   */
  public boolean isUnknownSubcommand(DbuffCommand command, String subcommand) {
    List<String> known = subcommandNames(command.getDefinition());
    if (known.isEmpty()) {
      return false;
    }
    return subcommand == null
        || known.stream().noneMatch(name -> name.equalsIgnoreCase(subcommand));
  }

  /**
   * Help embed generated from the registered definitions.
   *
   * @return the rendered help embed
   */
  public MessageEmbed buildHelpEmbed() {
    StringBuilder description = new StringBuilder();

    for (DbuffCommand command : commands) {
      SlashCommandData definition = command.getDefinition();
      List<String> subcommands = subcommandNames(definition);

      if (subcommands.isEmpty()) {
        description
            .append("`/")
            .append(definition.getName())
            .append("` — ")
            .append(definition.getDescription())
            .append('\n');
      } else {
        description.append("**/").append(definition.getName()).append("**\n");
        for (SubcommandData subcommand : definition.getSubcommands()) {
          description
              .append("`/")
              .append(definition.getName())
              .append(' ')
              .append(subcommand.getName())
              .append("` — ")
              .append(subcommand.getDescription())
              .append('\n');
        }
      }

      if (!command.getTextAliases().isEmpty()) {
        description
            .append("*also: ")
            .append(String.join(", ", command.getTextAliases().stream().map(a -> "!" + a).toList()))
            .append("*\n");
      }
      description.append('\n');
    }

    return new EmbedBuilder()
        .setTitle("📖 DBuff Commands")
        .setDescription(description.toString().trim())
        .setColor(EMBED_COLOR)
        .build();
  }

  private List<String> subcommandNames(SlashCommandData definition) {
    List<String> names = new ArrayList<>();
    definition.getSubcommands().forEach(subcommand -> names.add(subcommand.getName()));
    return names;
  }
}
