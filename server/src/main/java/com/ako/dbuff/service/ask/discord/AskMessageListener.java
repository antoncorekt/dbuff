package com.ako.dbuff.service.ask.discord;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.AskThreadRepo;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.service.ask.AskProperties;
import com.ako.dbuff.service.ask.AskQuota;
import com.ako.dbuff.service.ask.discord.AskThreadRunner.AskRequest;
import com.ako.dbuff.service.ask.discord.AskTrigger.Decision;
import com.ako.dbuff.service.ask.discord.AskTrigger.MessageFacts;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.discord.command.TextCommandProperties;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/** Turns focus-group messages into questions for Claude, in a new thread or an ask thread. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AskMessageListener extends ListenerAdapter {

  static final String LINK_FIRST = "Link your account first: `/dbuff link`";
  static final String DAILY_LIMIT = "Daily question limit reached for this channel.";
  static final String THREAD_FULL = "This thread is full, start a new one.";

  private final AskProperties properties;
  private final AskThreadRepo threadRepo;
  private final AskQuota quota;
  private final AskThreadRunner runner;
  private final DbufInstanceConfigService instanceConfigService;
  private final PlayerRepo playerRepo;
  private final PlayerReferenceResolver playerResolver;
  private final TextCommandProperties textProperties;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (!properties.isEnabled() || event.getAuthor().isBot()) {
      return;
    }
    Thread.startVirtualThread(
        () -> {
          try {
            handle(event);
          } catch (Exception e) {
            log.error("Ask listener failed for message {}", event.getMessageId(), e);
          }
        });
  }

  private void handle(MessageReceivedEvent event) {
    Message message = event.getMessage();
    ThreadChannel thread = event.getChannel() instanceof ThreadChannel t ? t : null;
    TextChannel text = event.getChannel() instanceof TextChannel c ? c : null;
    if (thread == null && text == null) {
      return;
    }

    Decision decision =
        AskTrigger.decide(
            new MessageFacts(
                event.getAuthor().isBot(),
                thread != null,
                thread != null && threadRepo.existsById(thread.getId()),
                message.getContentRaw(),
                message.getAttachments().stream().anyMatch(Message.Attachment::isImage),
                textProperties.getTextPrefix()));
    if (decision == Decision.IGNORE) {
      return;
    }
    boolean followUp = decision == Decision.FOLLOW_UP;

    String channelId = followUp ? thread.getParentChannel().getId() : text.getId();
    Optional<DbufInstanceConfigResponse> instance =
        instanceConfigService.getByDiscordChannelId(channelId);
    if (instance.isEmpty()
        || !Boolean.TRUE.equals(instance.get().getActive())
        || instance.get().getSettings() == null
        || !instance.get().getSettings().isAskEnabled()) {
      return;
    }
    String instanceId = instance.get().getId();

    String discordUserId = event.getAuthor().getId();
    List<ResolvedPlayer> focusGroup = playerResolver.focusGroup(channelId);
    ResolvedPlayer asker = findAsker(discordUserId, focusGroup);
    if (asker == null) {
      if (!followUp) {
        message.reply(LINK_FIRST).queue();
      }
      return;
    }
    if (!quota.canAsk(instanceId, Instant.now())) {
      message.reply(DAILY_LIMIT).queue();
      return;
    }
    if (followUp && quota.threadFull(thread.getId())) {
      message.reply(THREAD_FULL).queue();
      return;
    }

    AskRequest request =
        new AskRequest(
            instanceId,
            channelId,
            asker,
            discordUserId,
            focusGroup,
            instance.get().getGameModes(),
            message.getContentRaw());
    if (followUp) {
      runner.followUp(thread, request);
    } else {
      runner.startThread(message, request);
    }
  }

  private ResolvedPlayer findAsker(String discordUserId, List<ResolvedPlayer> focusGroup) {
    Optional<PlayerDomain> player = playerRepo.findByDiscordUserId(discordUserId);
    if (player.isEmpty()) {
      return null;
    }
    return focusGroup.stream()
        .filter(member -> member.accountId().equals(player.get().getId()))
        .findFirst()
        .orElse(null);
  }
}
