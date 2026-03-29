package com.ako.dbuff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "discord")
public class DiscordConfig {

  private String apiKey;

  private String matchSummaryChannelId;
}
