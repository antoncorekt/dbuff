package com.ako.dbuff.service.discord;

import com.ako.dbuff.config.DiscordConfig;
import com.ako.dbuff.service.discord.command.adapter.SlashCommandAdapter;
import com.ako.dbuff.service.discord.command.adapter.TextCommandAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfiguration {

  /**
   * Registers the three listeners that make up the whole Discord surface.
   *
   * <p>{@link SlashCommandAdapter} serves every {@code /} command and its autocomplete; {@link
   * TextCommandAdapter} keeps the legacy {@code !} forms working; {@link ScoreboardButtonListener}
   * offers scoreboard scouting on images.
   *
   * <p>Exactly one listener may answer any given input. The old per-feature listeners were deleted
   * rather than left alongside these, because both surfaces recognised {@code !dbuf} and {@code
   * !vs} and every such command would have been answered twice.
   *
   * <p>{@code MESSAGE_CONTENT} stays enabled because two of the three need it — the text aliases to
   * read commands at all, and the button listener to see that a message carries an image.
   */
  @Bean
  public JDA jdaApi(
      DiscordConfig config,
      SlashCommandAdapter slashCommandAdapter,
      TextCommandAdapter textCommandAdapter,
      ScoreboardButtonListener scoreboardButtonListener) {
    return JDABuilder.createDefault(config.getApiKey())
        .addEventListeners(slashCommandAdapter)
        .addEventListeners(textCommandAdapter)
        .addEventListeners(scoreboardButtonListener)
        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
        .build();
  }
}
