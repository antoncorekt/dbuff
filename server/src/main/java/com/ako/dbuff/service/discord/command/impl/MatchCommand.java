package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.discord.command.AsyncReply;
import com.ako.dbuff.service.discord.command.CommandContext;
import com.ako.dbuff.service.discord.command.DbuffCommand;
import com.ako.dbuff.service.match.DotaApiParseRequestService;
import com.ako.dbuff.service.match.MatchDeletionService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.scheduler.MatchParseSchedulerService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code /match} — reprocess the match a thread is about.
 *
 * <p>Thread-only, and the match ID comes from the thread's name rather than an option. That is
 * deliberate: the report threads are named {@code Match <id>} when they are created, so the ID is
 * already unambiguous where these commands are used, and an option would let someone rerun an
 * unrelated match from the wrong thread.
 *
 * <p>{@code rerun} re-reports from data already stored. {@code retry} throws that data away and
 * re-fetches from OpenDota — slower, and the right answer only when the stored data is wrong.
 */
@Slf4j
@Component
public class MatchCommand implements DbuffCommand {

  /** Report threads are created with this prefix; the rest of the name is the match ID. */
  private static final String THREAD_PREFIX = "Match ";

  private final MatchRepo matchRepo;
  private final DbufInstanceConfigRepository instanceConfigRepo;
  private final MatchReportOrchestrator matchReportOrchestrator;
  private final MatchDeletionService matchDeletionService;
  private final DotaApiParseRequestService parseRequestService;
  private final MatchParseSchedulerService matchParseSchedulerService;

  public MatchCommand(
      MatchRepo matchRepo,
      DbufInstanceConfigRepository instanceConfigRepo,
      @Lazy MatchReportOrchestrator matchReportOrchestrator,
      @Lazy MatchDeletionService matchDeletionService,
      @Lazy DotaApiParseRequestService parseRequestService,
      @Lazy MatchParseSchedulerService matchParseSchedulerService) {
    // Lazy for the same reason the old listener was: these pull in the whole match pipeline.
    this.matchRepo = matchRepo;
    this.instanceConfigRepo = instanceConfigRepo;
    this.matchReportOrchestrator = matchReportOrchestrator;
    this.matchDeletionService = matchDeletionService;
    this.parseRequestService = parseRequestService;
    this.matchParseSchedulerService = matchParseSchedulerService;
  }

  @Override
  public String getName() {
    return "match";
  }

  /** The aliases <em>are</em> the subcommands: {@code !rerun} means {@code /match rerun}. */
  @Override
  public List<String> getTextAliases() {
    return List.of("rerun", "retry");
  }

  @Override
  public Map<String, String> parseTextArguments(String alias, String subcommand, String arguments) {
    return Map.of();
  }

  @Override
  public String resolveTextSubcommand(String alias, String parsedSubcommand) {
    return alias;
  }

  @Override
  public SlashCommandData getDefinition() {
    return Commands.slash("match", "Reprocess the match this thread is about")
        .addSubcommands(
            new SubcommandData("rerun", "Re-report using the data already stored"),
            new SubcommandData("retry", "Delete the stored data and re-fetch from OpenDota"));
  }

  @Override
  public void execute(String subcommand, CommandContext context) {
    if (!context.isInsideThread()) {
      context.replyEphemeral(
          "❌ Use `/match "
              + (subcommand == null ? "rerun" : subcommand)
              + "` inside a match thread — the match ID comes from the thread name.");
      return;
    }

    String threadName = context.getThreadName().orElse(null);
    Long matchId = parseMatchId(threadName);
    if (matchId == null) {
      context.replyEphemeral(
          "❌ Could not read a match ID from the thread name `" + threadName + "`.");
      return;
    }

    Optional<DbufInstanceConfigDomain> config =
        instanceConfigRepo.findByDiscordChannelId(context.getParentChannelId());
    if (config.isEmpty()) {
      context.replyEphemeral(
          "❌ No configuration found for this channel. Use `/dbuff register` first.");
      return;
    }

    if ("retry".equals(subcommand)) {
      retry(context, matchId, config.get());
    } else {
      rerun(context, matchId, config.get());
    }
  }

  private void rerun(CommandContext context, Long matchId, DbufInstanceConfigDomain config) {
    Optional<MatchDomain> match = matchRepo.findById(matchId);
    if (match.isEmpty()) {
      context.replyEphemeral(
          "❌ Match " + matchId + " is not in the database. Use `/match retry` to fetch it.");
      return;
    }

    AsyncReply reply =
        context.acknowledge("🔄 Rerunning analysis for match " + matchId + "…", "Match " + matchId);
    try {
      log.info(
          "Rerunning report for match {} in channel {}", matchId, config.getDiscordChannelId());
      matchReportOrchestrator.processAndReport(List.of(match.get()), config);
    } catch (Exception e) {
      log.error("Failed to rerun report for match {}", matchId, e);
      reply.fail("❌ Failed to rerun match " + matchId + ": " + e.getMessage());
    }
  }

  private void retry(CommandContext context, Long matchId, DbufInstanceConfigDomain config) {
    AsyncReply reply =
        context.acknowledge(
            "🔄 Retrying match " + matchId + " — deleting stored data and reprocessing…",
            "Match " + matchId);

    try {
      if (matchRepo.findById(matchId).isPresent()) {
        matchDeletionService.deleteMatch(matchId);
      }

      MatchDomain match = matchRepo.save(MatchDomain.builder().id(matchId).build());
      parseRequestService.submitParseRequest(matchId);
      match.setParseRequested(true);
      match.setParseRequestedAt(LocalDateTime.now());
      matchRepo.save(match);

      PartialReportResult partialResult =
          matchReportOrchestrator.processAndReportPartial(match, config);

      // Falls back to the current thread when the orchestrator did not create its own, so the
      // scheduler still knows where to post the completed analysis.
      Long threadId =
          partialResult != null ? partialResult.threadId() : parseLong(context.getChannelId());
      Long headerMessageId = partialResult != null ? partialResult.headerMessageId() : null;
      matchParseSchedulerService.scheduleParseWait(
          matchId, config.getId(), threadId, headerMessageId);

      reply.post("✅ Parse requested. Full analysis will appear when parsing completes.");
    } catch (Exception e) {
      log.error("Failed to retry match {}", matchId, e);
      reply.fail("❌ Failed to retry match " + matchId + ": " + e.getMessage());
    }
  }

  /** {@code "Match 8795480597"} to {@code 8795480597}, or null when the name is anything else. */
  private Long parseMatchId(String threadName) {
    if (threadName == null || !threadName.startsWith(THREAD_PREFIX)) {
      return null;
    }
    return parseLong(threadName.substring(THREAD_PREFIX.length()).trim());
  }

  private Long parseLong(String raw) {
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
