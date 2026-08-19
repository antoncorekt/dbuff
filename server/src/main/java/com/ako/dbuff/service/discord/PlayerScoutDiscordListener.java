package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.ExternalPlayerStatisticService;
import com.ako.dbuff.service.ranking.ScoreboardStatisticService;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Discord listener that reports how a channel's focus group performed against other players.
 *
 * <p>Two triggers, both replying inside a freshly created thread:
 *
 * <ol>
 *   <li><b>{@code !vs <player_name>}</b> — statistics for a single named player.
 *   <li><b>An image attachment</b> — treated as a scoreboard screenshot: the player names are
 *       detected, the focus group's own players are excluded, and statistics are posted for each
 *       remaining opponent.
 * </ol>
 *
 * <p>Both triggers require the channel to have a registered instance (they resolve the focus group
 * from it); messages in channels without one are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerScoutDiscordListener extends ListenerAdapter {

  private static final String COMMAND_PREFIX = "!vs";
  private static final int MAX_THREAD_NAME_LENGTH = 100;

  private final DbufInstanceConfigService instanceConfigService;
  private final ExternalPlayerStatisticService externalPlayerStatisticService;
  private final ScoreboardStatisticService scoreboardStatisticService;
  private final DiscordStatisticFormatter formatter;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) {
      return;
    }
    // Threads can only be created on messages in a regular text channel.
    if (!(event.getChannel() instanceof TextChannel textChannel)) {
      return;
    }

    Message message = event.getMessage();
    String content = message.getContentRaw().trim();

    if (content.equalsIgnoreCase(COMMAND_PREFIX)
        || content.toLowerCase().startsWith(COMMAND_PREFIX + " ")) {
      String playerName = content.substring(COMMAND_PREFIX.length()).trim();
      handleVsCommand(textChannel, message, playerName);
      return;
    }

    // No command: an image attachment is treated as a scoreboard to scout.
    Optional<Attachment> image =
        message.getAttachments().stream().filter(Attachment::isImage).findFirst();
    image.ifPresent(attachment -> handleScoreboard(textChannel, message, attachment));
  }

  private void handleVsCommand(TextChannel channel, Message message, String playerName) {
    if (playerName.isBlank()) {
      channel.sendMessage("Usage: `!vs <player_name>` (case-insensitive, supports regex)").queue();
      return;
    }
    Optional<String> instanceId = resolveInstanceId(channel);
    if (instanceId.isEmpty()) {
      channel
          .sendMessage("❌ No instance registered for this channel. Use `!dbuf register` first.")
          .queue();
      return;
    }

    runAsync(
        () -> {
          ThreadChannel thread = createThread(message, "vs " + playerName);
          List<ExternalPlayerStatisticResponse> matches =
              externalPlayerStatisticService.getStatisticsByNamePatternForInstance(
                  instanceId.get(), playerName);

          if (matches.isEmpty()) {
            thread.sendMessage("No players matched `" + playerName + "`.").complete();
            return;
          }
          if (matches.size() > 1) {
            thread.sendMessage("Matched **" + matches.size() + "** players:").complete();
          }
          for (ExternalPlayerStatisticResponse match : matches) {
            post(thread, formatter.formatPlayer(match));
          }
        },
        channel,
        "!vs " + playerName);
  }

  private void handleScoreboard(TextChannel channel, Message message, Attachment attachment) {
    Optional<String> instanceId = resolveInstanceId(channel);
    if (instanceId.isEmpty()) {
      // Ignore images in channels that are not set up for tracking.
      return;
    }

    runAsync(
        () -> {
          ThreadChannel thread = createThread(message, "Scout: " + attachment.getFileName());
          thread.sendMessage("🔍 Reading scoreboard…").queue();

          byte[] imageBytes = download(attachment);
          List<ExternalPlayerStatisticResponse> opponents =
              scoreboardStatisticService.getStatisticsForInstance(instanceId.get(), imageBytes);

          if (opponents.isEmpty()) {
            thread.sendMessage("No opponents detected on the scoreboard.").complete();
            return;
          }

          thread.sendMessage("Found **" + opponents.size() + "** opponents:").complete();
          for (ExternalPlayerStatisticResponse opponent : opponents) {
            post(thread, formatter.formatPlayer(opponent));
          }
        },
        channel,
        "scoreboard scout");
  }

  private Optional<String> resolveInstanceId(TextChannel channel) {
    return instanceConfigService
        .getByDiscordChannelId(channel.getId())
        .map(DbufInstanceConfigResponse::getId);
  }

  private ThreadChannel createThread(Message message, String rawName) {
    String name = rawName.replaceAll("\\s+", " ").trim();
    if (name.length() > MAX_THREAD_NAME_LENGTH) {
      name = name.substring(0, MAX_THREAD_NAME_LENGTH);
    }
    return message.createThreadChannel(name).complete();
  }

  private byte[] download(Attachment attachment) {
    try (InputStream stream = attachment.getProxy().download().join()) {
      return stream.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to download image attachment", e);
    }
  }

  /** Sends each message chunk to the thread in order. */
  private void post(ThreadChannel thread, List<String> messages) {
    for (String messageContent : messages) {
      thread.sendMessage(messageContent).complete();
    }
  }

  /**
   * Runs the blocking work (OCR, DB queries, Discord calls) on a virtual thread so the JDA gateway
   * thread is never blocked. Errors are logged and reported back to the channel.
   */
  private void runAsync(Runnable task, TextChannel channel, String description) {
    Thread.startVirtualThread(
        () -> {
          try {
            task.run();
          } catch (Exception e) {
            log.error("Failed to handle {}: {}", description, e.getMessage(), e);
            channel
                .sendMessage("❌ Failed to process " + description + ": " + e.getMessage())
                .queue();
          }
        });
  }
}
