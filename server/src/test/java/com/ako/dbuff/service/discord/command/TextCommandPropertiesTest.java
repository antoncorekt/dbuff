package com.ako.dbuff.service.discord.command;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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
  void aBlankValueFailsBindingWithAClearMessage() {
    Binder binder =
        new Binder(new MapConfigurationPropertySource(Map.of("discord.commands.text-prefix", " ")));

    assertThatThrownBy(() -> binder.bind("discord.commands", TextCommandProperties.class).get())
        .hasRootCauseMessage(
            "discord.commands.text-prefix must be 1-3 non-whitespace characters not starting with"
                + " '/', was: ' '");
  }

  @ParameterizedTest
  @ValueSource(strings = {"~", "~~", "$$$", ".", "!!"})
  void acceptsOneToThreeNonWhitespaceCharacters(String prefix) {
    assertThat(isValid(prefix)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  ", "~~~~", "/", "/x", "~ ", "a b"})
  void rejectsBlankSlashLeadingTooLongOrSpaced(String prefix) {
    assertThat(isValid(prefix)).isFalse();
  }

  @Test
  void rejectsNull() {
    assertThat(isValid(null)).isFalse();
  }
}
