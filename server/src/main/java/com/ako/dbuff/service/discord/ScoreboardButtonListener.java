package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.ScoreboardStatisticService;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Offers scoreboard scouting on images, instead of performing it on all of them.
 *
 * <p>The previous behaviour ran OCR plus an OpenAI Vision call on <em>every</em> image posted in a
 * registered channel — memes, clips and screenshots of anything at all. This attaches a button and
 * waits to be asked, so an unwanted scout costs nothing.
 *
 * <p>Only the click path does work, and it does that work on a virtual thread so the JDA gateway
 * thread is never blocked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreboardButtonListener extends ListenerAdapter {

  /** Button IDs carry the message ID so the click knows which image to read. */
  static final String BUTTON_PREFIX = "scout-scoreboard:";

  static final String BUTTON_LABEL = "🔍 Scout this scoreboard";

  private static final int MAX_THREAD_NAME_LENGTH = 100;

  private final DbufInstanceConfigService instanceConfigService;
  private final ScoreboardStatisticService scoreboardStatisticService;
  private final DiscordStatisticFormatter formatter;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) {
      return;
    }
    // Buttons are attached to messages in text channels; a thread has no scoreboard flow.
    if (!(event.getChannel() instanceof TextChannel textChannel)) {
      return;
    }
    if (instanceId(textChannel.getId()).isEmpty()) {
      return;
    }

    Message message = event.getMessage();
    boolean hasImage = message.getAttachments().stream().anyMatch(Attachment::isImage);
    if (!hasImage) {
      return;
    }

    message
        .reply("Scoreboard?")
        .addComponents(ActionRow.of(Button.primary(BUTTON_PREFIX + message.getId(), BUTTON_LABEL)))
        .queue();
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String componentId = event.getComponentId();
    if (componentId == null || !componentId.startsWith(BUTTON_PREFIX)) {
      return;
    }
    String messageId = componentId.substring(BUTTON_PREFIX.length());

    Optional<String> instanceId = instanceId(event.getChannel().getId());
    if (instanceId.isEmpty()) {
      event.reply("❌ This channel is no longer tracking players.").setEphemeral(true).queue();
      return;
    }

    // Acknowledge inside Discord's three-second window, then do the slow part elsewhere.
    event.deferReply().complete();
    // Strip the button from its own message so a second click cannot pay for the same
    // Vision call twice.
    event.getMessage().editMessageComponents(List.of()).queue();

    Thread.startVirtualThread(() -> scout(event, messageId, instanceId.get()));
  }

  private void scout(ButtonInteractionEvent event, String messageId, String instanceId) {
    try {
      Message original = event.getChannel().retrieveMessageById(messageId).complete();
      Optional<Attachment> image =
          original.getAttachments().stream().filter(Attachment::isImage).findFirst();
      if (image.isEmpty()) {
        event.getHook().sendMessage("❌ That message no longer has an image.").complete();
        return;
      }

      ThreadChannel thread =
          original
              .createThreadChannel(threadName("Scout: " + image.get().getFileName()))
              .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
              .complete();
      event.getHook().sendMessage("🔍 Reading scoreboard in " + thread.getAsMention()).complete();

      List<ExternalPlayerStatisticResponse> opponents =
          scoreboardStatisticService.getStatisticsForInstance(instanceId, download(image.get()));
      if (opponents.isEmpty()) {
        thread.sendMessage("No opponents detected on the scoreboard.").complete();
        return;
      }

      thread.sendMessage("Found **" + opponents.size() + "** opponents:").complete();
      for (ExternalPlayerStatisticResponse opponent : opponents) {
        for (String chunk : formatter.formatPlayer(opponent)) {
          thread.sendMessage(chunk).complete();
        }
      }
    } catch (Exception e) {
      log.error("Scoreboard scout failed for message {}", messageId, e);
      event.getHook().sendMessage("❌ Could not scout that scoreboard: " + e.getMessage()).queue();
    }
  }

  private Optional<String> instanceId(String channelId) {
    return instanceConfigService
        .getByDiscordChannelId(channelId)
        .map(DbufInstanceConfigResponse::getId);
  }

  private String threadName(String raw) {
    String name = raw.replaceAll("\\s+", " ").trim();
    return name.length() > MAX_THREAD_NAME_LENGTH
        ? name.substring(0, MAX_THREAD_NAME_LENGTH)
        : name;
  }

  private byte[] download(Attachment attachment) {
    try (InputStream stream = attachment.getProxy().download().join()) {
      return stream.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to download image attachment", e);
    }
  }
}
