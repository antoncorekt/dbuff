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

  private static final Pattern VALID_PREFIX = Pattern.compile("(?!/)\\S{1,3}");

  /** One to three non-whitespace characters, not starting with {@code /} (slash commands). */
  private String textPrefix = "!";

  /**
   * Rejects an invalid value at binding time, so the application fails to start instead of running
   * with a prefix that can never match or that collides with slash commands.
   */
  public void setTextPrefix(String textPrefix) {
    if (textPrefix == null || !VALID_PREFIX.matcher(textPrefix).matches()) {
      throw new IllegalArgumentException(
          "discord.commands.text-prefix must be 1-3 non-whitespace characters not starting with"
              + " '/', was: '"
              + textPrefix
              + "'");
    }
    this.textPrefix = textPrefix;
  }
}
