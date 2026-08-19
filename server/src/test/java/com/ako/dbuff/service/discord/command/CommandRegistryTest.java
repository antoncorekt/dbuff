package com.ako.dbuff.service.discord.command;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryTest {

  private StubCommand stats;
  private StubCommand scout;
  private CommandRegistry registry;

  @BeforeEach
  void setUp() {
    stats =
        new StubCommand(
            "stats",
            Commands.slash("stats", "Player statistics")
                .addSubcommands(
                    new SubcommandData("overall", "Overall performance"),
                    new SubcommandData("items", "Item builds")),
            List.of());
    scout = new StubCommand("scout", Commands.slash("scout", "Scout an opponent"), List.of("vs"));

    registry = new CommandRegistry(List.of(stats, scout));
  }

  @Test
  void findByName_returnsTheCommand() {
    assertThat(registry.findByName("stats")).contains(stats);
    assertThat(registry.findByName("scout")).contains(scout);
  }

  @Test
  void findByName_isCaseInsensitive() {
    assertThat(registry.findByName("STATS")).contains(stats);
  }

  @Test
  void findByName_unknown_isEmpty() {
    assertThat(registry.findByName("nope")).isEmpty();
  }

  @Test
  void findByTextAlias_resolvesAliasesAndRootNames() {
    assertThat(registry.findByTextAlias("vs")).contains(scout);
    assertThat(registry.findByTextAlias("stats")).contains(stats);
  }

  @Test
  void findByTextAlias_unknown_isEmpty() {
    assertThat(registry.findByTextAlias("nope")).isEmpty();
  }

  @Test
  void getDefinitions_returnsOnePerCommand() {
    assertThat(registry.getDefinitions()).hasSize(2);
  }

  @Test
  void suggestCommand_offersTheNearestName() {
    assertThat(registry.suggestCommand("stat")).contains("stats");
    assertThat(registry.suggestCommand("scot")).contains("scout");
  }

  @Test
  void suggestSubcommand_offersTheNearestSubcommandOfThatCommand() {
    assertThat(registry.suggestSubcommand("stats", "overal")).contains("overall");
    assertThat(registry.suggestSubcommand("stats", "item")).contains("items");
  }

  @Test
  void suggestSubcommand_unknownCommand_isEmpty() {
    assertThat(registry.suggestSubcommand("nope", "overall")).isEmpty();
  }

  @Test
  void suggestSubcommand_commandWithoutSubcommands_isEmpty() {
    assertThat(registry.suggestSubcommand("scout", "anything")).isEmpty();
  }

  @Test
  void isUnknownSubcommand_detectsTyposAndMissingTokens() {
    assertThat(registry.isUnknownSubcommand(stats, "overall")).isFalse();
    assertThat(registry.isUnknownSubcommand(stats, "OVERALL")).isFalse();
    assertThat(registry.isUnknownSubcommand(stats, "overal")).isTrue();
    assertThat(registry.isUnknownSubcommand(stats, null)).isTrue();
  }

  @Test
  void isUnknownSubcommand_isAlwaysFalseForCommandsWithoutSubcommands() {
    assertThat(registry.isUnknownSubcommand(scout, null)).isFalse();
    assertThat(registry.isUnknownSubcommand(scout, "anything")).isFalse();
  }

  @Test
  void buildHelpEmbed_listsEverySubcommandAndAlias() {
    String rendered = registry.buildHelpEmbed().getDescription();

    assertThat(rendered)
        .contains("/stats overall")
        .contains("/stats items")
        .contains("/scout")
        .contains("!vs");
  }

  /** Minimal DbuffCommand for registry tests — does nothing when executed. */
  private static class StubCommand implements DbuffCommand {
    private final String name;
    private final SlashCommandData definition;
    private final List<String> aliases;

    StubCommand(String name, SlashCommandData definition, List<String> aliases) {
      this.name = name;
      this.definition = definition;
      this.aliases = aliases;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public SlashCommandData getDefinition() {
      return definition;
    }

    @Override
    public List<String> getTextAliases() {
      return aliases;
    }

    @Override
    public void execute(String subcommand, CommandContext context) {
      // no-op
    }
  }
}
