package com.ako.dbuff.service.discord.icon;

import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bound from {@code discord.icons.*}. Icons are off until explicitly enabled. */
@Data
@Configuration
@ConfigurationProperties(prefix = "discord.icons")
public class IconProperties {
  private boolean enabled = false;

  /** No emoji is created once this many are in use; Discord allows 2000 per application. */
  private int maxEmoji = 1900;

  private Path directory = Path.of("icons");
  private String cdnUrl = "https://cdn.cloudflare.steamstatic.com";
  private int sizePx = 128;

  /** Pause after each emoji created, on top of JDA's own rate limit handling. */
  private long createDelayMillis = 500;

  /** A Unicode symbol stands in where no emoji is synced; off means plain text only. */
  private boolean placeholders = true;
}
