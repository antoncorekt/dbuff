package com.ako.dbuff.service.discord.command;

import java.util.regex.Pattern;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code discord.commands.*}. The text prefix lets a local test bot share a server with
 * the production bot without both answering the same message.
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "discord.commands")
public class TextCommandProperties {

  private static final String DEFAULT_PREFIX = "!";
  private static final Pattern VALID_PREFIX = Pattern.compile("(?!/)[^\\p{L}\\p{N}\\s]{1,3}");

  /**
   * One to three characters that are neither letters, digits nor whitespace, not starting with
   * {@code /} (slash commands). A blank or unset value falls back to the default {@code !}.
   */
  private String textPrefix = DEFAULT_PREFIX;

  /**
   * Rejects an invalid non-blank value at binding time, so the application fails to start instead
   * of running with a prefix that collides with slash commands or ordinary chat. A blank value (for
   * example {@code DISCORD_COMMANDS_TEXT_PREFIX=} in a deploy file) keeps the default.
   */
  public void setTextPrefix(String textPrefix) {
    if (textPrefix == null || textPrefix.isBlank()) {
      this.textPrefix = DEFAULT_PREFIX;
      return;
    }
    if (!VALID_PREFIX.matcher(textPrefix).matches()) {
      throw new IllegalArgumentException(
          "discord.commands.text-prefix must be 1-3 characters, none a letter, digit or whitespace,"
              + " not starting with '/', was: '"
              + textPrefix
              + "'");
    }
    this.textPrefix = textPrefix;
  }
}
