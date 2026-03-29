package com.ako.dbuff.service.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@Slf4j
public class PingPongListener extends ListenerAdapter {

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) return;
    // We don't want to respond to other bot accounts, including ourself
    Message message = event.getMessage();
    String content = message.getContentRaw();
    // getContentRaw() is an atomic getter
    // getContentDisplay() is a lazy getter which modifies the content for e.g. console view (strip
    // discord formatting)
    if (content.equals("!ping")) {
      MessageChannel channel = event.getChannel();
      String id = channel.getId();
      log.info("chat id {}", id);

      message
          .createThreadChannel("Thread test")
          .queue(
              thread -> {
                thread.sendMessage("Some message in thread").queue();
              });
      //      channel.sendMessage("Pong!").queue(); // Important to call .queue() on the RestAction
      // returned by sendMessage(...)
    }
  }
}
