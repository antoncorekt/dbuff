package com.ako.dbuff.service.discord.command;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextCommandPropertiesTest {

  private boolean isValid(String prefix) {
    try {
      new TextCommandProperties().setTextPrefix(prefix);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Test
  void defaultsToTheLegacyBang() {
    assertThat(new TextCommandProperties().getTextPrefix()).isEqualTo("!");
    assertThat(isValid("!")).isTrue();
  }

  @Test
  void bindsTheKebabCaseProperty() {
    TextCommandProperties bound =
        new Binder(new MapConfigurationPropertySource(Map.of("discord.commands.text-prefix", "~")))
            .bind("discord.commands", TextCommandProperties.class)
            .get();

    assertThat(bound.getTextPrefix()).isEqualTo("~");
  }

  @Test
  void anInvalidValueFailsBindingWithAClearRootCause() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(Map.of("discord.commands.text-prefix", "/x")));

    assertThatThrownBy(() -> binder.bind("discord.commands", TextCommandProperties.class).get())
        .hasRootCauseMessage(
            "discord.commands.text-prefix must be 1-3 characters, none a letter, digit or"
                + " whitespace, not starting with '/', was: '/x'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"~", "~~", "$$$", ".", "!!", "§"})
  void acceptsOneToThreeNonWhitespaceCharacters(String prefix) {
    assertThat(isValid(prefix)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"~~~~", "/", "/x", "~ ", "a b", "a", "dbuf", "1", "~a"})
  void rejectsSlashLeadingTooLongSpacedLettersOrDigits(String prefix) {
    assertThat(isValid(prefix)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void blankFallsBackToTheDefault(String blank) {
    TextCommandProperties properties = new TextCommandProperties();
    properties.setTextPrefix("~");
    properties.setTextPrefix(blank);

    assertThat(properties.getTextPrefix()).isEqualTo("!");
  }

  @Test
  void nullFallsBackToTheDefault() {
    TextCommandProperties properties = new TextCommandProperties();
    properties.setTextPrefix(null);

    assertThat(properties.getTextPrefix()).isEqualTo("!");
  }

  @Configuration
  @EnableConfigurationProperties(TextCommandProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(Config.class);

  @Test
  void realApplicationPropertiesDefaultIsTheBang() {
    runner.run(
        context ->
            assertThat(context.getBean(TextCommandProperties.class).getTextPrefix())
                .isEqualTo("!"));
  }

  @Test
  void environmentVariableOverridesTheDefault() {
    runner
        .withPropertyValues("DISCORD_COMMANDS_TEXT_PREFIX=~")
        .run(
            context ->
                assertThat(context.getBean(TextCommandProperties.class).getTextPrefix())
                    .isEqualTo("~"));
  }

  @Test
  void aSetButEmptyEnvironmentVariableKeepsTheDefault() {
    runner
        .withPropertyValues("DISCORD_COMMANDS_TEXT_PREFIX=")
        .run(
            context ->
                assertThat(context.getBean(TextCommandProperties.class).getTextPrefix())
                    .isEqualTo("!"));
  }

  @Test
  void anInvalidEnvironmentVariableFailsStartupWithTheClearMessage() {
    runner
        .withPropertyValues("DISCORD_COMMANDS_TEXT_PREFIX=/x")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("discord.commands.text-prefix must be 1-3 characters")
                    .hasMessageContaining("was: '/x'"));
  }
}
