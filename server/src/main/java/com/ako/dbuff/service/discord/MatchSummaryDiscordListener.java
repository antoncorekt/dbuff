package com.ako.dbuff.service.discord;

import com.ako.dbuff.config.DiscordConfig;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MatchSummaryDiscordListener extends ListenerAdapter {

  private final DiscordConfig discordConfig;

  public MatchSummaryDiscordListener(DiscordConfig discordConfig) {
    this.discordConfig = discordConfig;
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (!discordConfig.getMatchSummaryChannelId().equals(event.getChannel().getId())) {
      return;
    }
    if (!event.getAuthor().isBot()) return;
    // We don't want to respond to other bot accounts, including ourself
    Message message = event.getMessage();
    String content = message.getContentRaw();
    // getContentRaw() is an atomic getter
    // getContentDisplay() is a lazy getter which modifies the content for e.g. console view (strip
    // discord formatting)
    if (content.equals("!ping")) {
      MessageChannel channel = event.getChannel();
      channel
          .sendMessage("Pong!")
          .queue(); // Important to call .queue() on the RestAction returned by sendMessage(...)
    }
  }
}
