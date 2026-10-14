package com.ako.dbuff.service.discord;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
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
   * Sends Components V2 to a text channel; the message then carries only the components.
   *
   * @return the sent Message, or null if the channel was not found
   */
  public Message sendChannelComponents(
      String channelId, List<MessageTopLevelComponent> components) {
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      log.warn("Discord text channel not found: {}", channelId);
      return null;
    }
    return channel.sendMessageComponents(components).useComponentsV2(true).complete();
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

  /**
   * Sends Components V2 to a thread and waits for completion; the message then carries only the
   * components.
   */
  public Message sendThreadComponentsBlocking(
      ThreadChannel thread, List<MessageTopLevelComponent> components) {
    return thread.sendMessageComponents(components).useComponentsV2(true).complete();
  }

  public ThreadChannel getThreadById(long threadId) {
    return jda.getThreadChannelById(threadId);
  }

  public void editMessage(String channelId, long messageId, String newContent) {
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      log.warn("Discord text channel not found for edit: {}", channelId);
      return;
    }
    channel.editMessageById(messageId, newContent).queue();
  }

  /**
   * Replaces a message with Components V2 and waits for Discord's answer, so that a rejection
   * reaches the caller.
   */
  public void editComponentsMessage(
      String channelId, long messageId, List<MessageTopLevelComponent> components) {
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) {
      log.warn("Discord text channel not found for edit: {}", channelId);
      return;
    }
    channel.editMessageComponentsById(messageId, components).useComponentsV2(true).complete();
  }
}
