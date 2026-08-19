package com.ako.dbuff.service.discord;

import com.ako.dbuff.service.constant.ConstantPrecacheStarter;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.adapter.SlashCommandAdapter;
import com.ako.dbuff.service.discord.command.adapter.TextCommandAdapter;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real application context to prove the Discord command surface wires up.
 *
 * <p>This is the only test that would have caught the failure modes of assembling it: a
 * {@code @Lazy} cycle that does not resolve, a command bean whose dependencies are missing, or two
 * commands claiming the same name — which Discord rejects outright at registration, in production,
 * long after every unit test has passed.
 *
 * <p>{@link JDA} is mocked so nothing connects to Discord. That also stops {@code
 * BotConfiguration}'s factory method from running, so this does not assert the listeners are
 * attached; it asserts they all exist and are injectable, which is what could break here.
 */
@SpringBootTest
@ActiveProfiles("test")
class DiscordCommandWiringTest {

  @MockBean private JDA jda;

  /**
   * Mocked so its {@code @PostConstruct} does not run: it fetches every constant from OpenDota at
   * startup and throws if any comes back empty, which no test should depend on a network for.
   */
  @MockBean private ConstantPrecacheStarter constantPrecacheStarter;

  @Autowired private CommandRegistry registry;
  @Autowired private SlashCommandAdapter slashCommandAdapter;
  @Autowired private TextCommandAdapter textCommandAdapter;
  @Autowired private ScoreboardButtonListener scoreboardButtonListener;

  @Test
  void allThreeListenersAreAvailableForRegistration() {
    assertThat(slashCommandAdapter).isNotNull();
    assertThat(textCommandAdapter).isNotNull();
    assertThat(scoreboardButtonListener).isNotNull();
  }

  @Test
  void everyExpectedCommandIsRegistered() {
    assertThat(registry.getDefinitions())
        .extracting(SlashCommandData::getName)
        .contains("stats", "scout", "match", "dbuff");
  }

  /** Discord refuses a registration payload containing two commands with the same name. */
  @Test
  void noTwoCommandsShareAName() {
    List<String> names = registry.getDefinitions().stream().map(SlashCommandData::getName).toList();

    assertThat(names).doesNotHaveDuplicates();
  }

  @Test
  void noCommandHasDuplicateSubcommandNames() {
    for (SlashCommandData definition : registry.getDefinitions()) {
      assertThat(definition.getSubcommands().stream().map(SubcommandData::getName).toList())
          .as("subcommands of /%s", definition.getName())
          .doesNotHaveDuplicates();
    }
  }

  @Test
  void everyTextAliasResolvesToExactlyOneCommand() {
    for (SlashCommandData definition : registry.getDefinitions()) {
      registry
          .findByName(definition.getName())
          .orElseThrow()
          .getTextAliases()
          .forEach(
              alias ->
                  assertThat(registry.findByTextAlias(alias)).as("alias !%s", alias).isPresent());
    }
  }

  @Test
  void theLegacyAliasesStillResolve() {
    assertThat(registry.findByTextAlias("dbuf")).isPresent();
    assertThat(registry.findByTextAlias("vs")).isPresent();
    assertThat(registry.findByTextAlias("rerun")).isPresent();
    assertThat(registry.findByTextAlias("retry")).isPresent();
  }

  @Test
  void helpEmbedNamesEveryCommand() {
    String description = registry.buildHelpEmbed().getDescription();

    assertThat(description).isNotNull();
    for (SlashCommandData definition : registry.getDefinitions()) {
      assertThat(description).contains("/" + definition.getName());
    }
  }
}
