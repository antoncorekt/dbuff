package com.ako.dbuff.service.discord;

import com.ako.dbuff.config.DiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfiguration {

  @Bean
  public JDA jdaApi(DiscordConfig config, RegistrationDiscordListener registrationListener) {
    JDA api =
        JDABuilder.createDefault(config.getApiKey())
            .addEventListeners(new PingPongListener())
            .addEventListeners(registrationListener)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .build();

    return api;
  }
}
