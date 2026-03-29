package com.ako.dbuff.service.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordMessageService {

  private final JDA jda;

  /**
   * Sends a message to a Discord text channel.
   *
   * @param channelId the Discord channel ID
   * @param message the message content
   * @return the sent Message, or null if the channel was not found
   */
  public Message sendChannelMessage(String channelId, String message) {
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      log.warn("Discord text channel not found: {}", channelId);
      return null;
    }
    return channel.sendMessage(message).complete();
  }

  /**
   * Creates a thread on a parent message.
   *
   * @param parentMessage the message to create a thread on
   * @param threadName the name for the thread
   * @return the created ThreadChannel
   */
  public ThreadChannel createThread(Message parentMessage, String threadName) {
    return parentMessage.createThreadChannel(threadName).complete();
  }

  /**
   * Sends a message to a thread asynchronously.
   *
   * @param thread the thread channel
   * @param message the message content
   */
  public void sendThreadMessage(ThreadChannel thread, String message) {
    thread.sendMessage(message).queue();
  }

  /**
   * Sends a message to a thread and waits for completion.
   *
   * @param thread the thread channel
   * @param message the message content
   * @return the sent Message
   */
  public Message sendThreadMessageBlocking(ThreadChannel thread, String message) {
    return thread.sendMessage(message).complete();
  }
}
