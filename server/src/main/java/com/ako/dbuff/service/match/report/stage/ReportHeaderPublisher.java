package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.replay.report.view.ComponentsFallback;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import org.springframework.stereotype.Component;

/**
 * Posts and edits a match header as Components V2, falling back to its plain text when Discord
 * rejects the components. An edit that fails is logged and never reaches the caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportHeaderPublisher {
  private final DiscordMessageService discord;

  /** The header message; null when the channel is unknown. */
  public Message create(String channelId, ReportMessage.Components header) {
    try {
      return discord.sendChannelComponents(channelId, header.components());
    } catch (RuntimeException e) {
      if (!ComponentsFallback.payloadRejected(e)) {
        throw e;
      }
      log.warn("Header components rejected, posting plain text: {}", e.getMessage());
      return discord.sendChannelMessage(channelId, header.fallback());
    }
  }

  /** Whether the header was changed. */
  public boolean edit(String channelId, long messageId, ReportMessage.Components header) {
    try {
      discord.editComponentsMessage(channelId, messageId, header.components());
      return true;
    } catch (RuntimeException e) {
      if (ComponentsFallback.payloadRejected(e)) {
        log.warn(
            "Header components of message {} rejected, editing plain text: {}",
            messageId,
            e.getMessage());
        return editPlain(channelId, messageId, header);
      }
      log.warn("Could not edit header message {}: {}", messageId, e.getMessage());
      return false;
    }
  }

  private boolean editPlain(String channelId, long messageId, ReportMessage.Components header) {
    try {
      discord.editMessage(channelId, messageId, header.fallback());
      return true;
    } catch (RuntimeException e) {
      log.warn("Could not edit header message {} as plain text: {}", messageId, e.getMessage());
      return false;
    }
  }
}
