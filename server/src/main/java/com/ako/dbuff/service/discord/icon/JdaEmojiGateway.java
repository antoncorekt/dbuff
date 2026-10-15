package com.ako.dbuff.service.discord.icon;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdaEmojiGateway implements EmojiGateway {
  private final JDA jda;

  @Override
  public List<RemoteEmoji> list() {
    awaitReady();
    return jda.retrieveApplicationEmojis().complete().stream()
        .map(JdaEmojiGateway::remote)
        .toList();
  }

  @Override
  public RemoteEmoji create(String name, byte[] png) {
    awaitReady();
    return remote(jda.createApplicationEmoji(name, Icon.from(png)).complete());
  }

  @Override
  public void delete(long emojiId) {
    awaitReady();
    jda.retrieveApplicationEmojiById(emojiId).complete().delete().complete();
  }

  private void awaitReady() {
    try {
      jda.awaitReady();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Discord", e);
    }
  }

  private static RemoteEmoji remote(ApplicationEmoji emoji) {
    return new RemoteEmoji(emoji.getIdLong(), emoji.getName());
  }
}
