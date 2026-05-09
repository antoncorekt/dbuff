package com.ako.dbuff.service.discord;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.match.LastMatchesProcessorService;
import com.ako.dbuff.service.match.MatchDeletionService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PingPongListener extends ListenerAdapter {

  private final MatchRepo matchRepo;
  private final DbufInstanceConfigRepository instanceConfigRepo;
  private final MatchReportOrchestrator matchReportOrchestrator;
  private final MatchDeletionService matchDeletionService;
  private final LastMatchesProcessorService lastMatchesProcessorService;

  public PingPongListener(
      MatchRepo matchRepo,
      DbufInstanceConfigRepository instanceConfigRepo,
      @Lazy MatchReportOrchestrator matchReportOrchestrator,
      @Lazy MatchDeletionService matchDeletionService,
      @Lazy LastMatchesProcessorService lastMatchesProcessorService) {
    this.matchRepo = matchRepo;
    this.instanceConfigRepo = instanceConfigRepo;
    this.matchReportOrchestrator = matchReportOrchestrator;
    this.matchDeletionService = matchDeletionService;
    this.lastMatchesProcessorService = lastMatchesProcessorService;
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot()) return;

    Message message = event.getMessage();
    String content = message.getContentRaw().trim();

    if (content.equals("!rerun")) {
      handleRerun(event);
    } else if (content.equals("!retry")) {
      handleRetry(event);
    }
  }

  private void handleRerun(MessageReceivedEvent event) {
    if (!(event.getChannel() instanceof ThreadChannel thread)) {
      event.getChannel().sendMessage("Use !rerun inside a match thread.").queue();
      return;
    }

    String threadName = thread.getName();
    Long matchId = parseMatchIdFromThread(threadName);
    if (matchId == null) {
      thread.sendMessage("Could not parse match ID from thread name: " + threadName).queue();
      return;
    }

    String parentChannelId = thread.getParentChannel().getId();
    Optional<DbufInstanceConfigDomain> configOpt =
        instanceConfigRepo.findByDiscordChannelId(parentChannelId);
    if (configOpt.isEmpty()) {
      thread.sendMessage("No config found for channel " + parentChannelId).queue();
      return;
    }

    Optional<MatchDomain> matchOpt = matchRepo.findById(matchId);
    if (matchOpt.isEmpty()) {
      thread.sendMessage("Match " + matchId + " not found in database.").queue();
      return;
    }

    log.info("Rerunning report for match {} in channel {}", matchId, parentChannelId);
    thread.sendMessage("Rerunning analysis for match " + matchId + "...").queue();

    try {
      matchReportOrchestrator.processAndReport(List.of(matchOpt.get()), configOpt.get());
    } catch (Exception e) {
      log.error("Failed to rerun report for match {}: {}", matchId, e.getMessage(), e);
      thread.sendMessage("Failed: " + e.getMessage()).queue();
    }
  }

  private void handleRetry(MessageReceivedEvent event) {
    if (!(event.getChannel() instanceof ThreadChannel thread)) {
      event.getChannel().sendMessage("Use !retry inside a match thread.").queue();
      return;
    }

    String threadName = thread.getName();
    Long matchId = parseMatchIdFromThread(threadName);
    if (matchId == null) {
      thread.sendMessage("Could not parse match ID from thread name: " + threadName).queue();
      return;
    }

    String parentChannelId = thread.getParentChannel().getId();
    Optional<DbufInstanceConfigDomain> configOpt =
        instanceConfigRepo.findByDiscordChannelId(parentChannelId);
    if (configOpt.isEmpty()) {
      thread.sendMessage("No config found for channel " + parentChannelId).queue();
      return;
    }

    log.info("Retrying match {} from Discord command in channel {}", matchId, parentChannelId);
    thread
        .sendMessage("Retrying match " + matchId + "... Deleting old data and reprocessing.")
        .queue();

    try {
      if (matchRepo.findById(matchId).isPresent()) {
        matchDeletionService.deleteMatch(matchId);
      }

      DbufInstanceConfigDomain config = configOpt.get();
      lastMatchesProcessorService.processLastMatchesForInstance(config.getId(), false);

      thread
          .sendMessage("Parse requested. Full analysis will appear when parsing completes.")
          .queue();
    } catch (Exception e) {
      log.error("Failed to retry match {}: {}", matchId, e.getMessage(), e);
      thread.sendMessage("Failed: " + e.getMessage()).queue();
    }
  }

  private Long parseMatchIdFromThread(String threadName) {
    if (threadName == null) return null;
    // "Match 8795480597" -> 8795480597
    String prefix = "Match ";
    if (threadName.startsWith(prefix)) {
      try {
        return Long.parseLong(threadName.substring(prefix.length()).trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
