package com.ako.dbuff.service.discord.icon;

import java.util.List;

/** The bot's application emoji on Discord; the only place the icon code touches JDA. */
public interface EmojiGateway {
  record RemoteEmoji(long id, String name) {}

  List<RemoteEmoji> list();

  RemoteEmoji create(String name, byte[] png);

  void delete(long emojiId);
}
