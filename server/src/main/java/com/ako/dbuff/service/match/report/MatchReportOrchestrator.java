package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.analyzer.FullDataAvailable;
import com.ako.dbuff.service.match.report.analyzer.PartialDataAvailable;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import com.ako.dbuff.service.match.report.stage.HeaderRenderer;
import com.ako.dbuff.service.match.report.stage.MatchFacts;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService.HeaderKind;
import com.ako.dbuff.service.match.report.stage.MatchThreadRecorder;
import com.ako.dbuff.service.match.report.stage.NewMatchSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchReportOrchestrator {

  private final DiscordMessageService discordMessageService;
  private final List<ReportAnalyzer> analyzers;
  private final ReportFormatter reportFormatter;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final AbilityRepo abilityRepo;
  private final ItemRepository itemRepository;
  private final KillLogRepo killLogRepo;
  private final MatchThreadRecorder threadRecorder;
  private final HeaderRenderer headerRenderer;
  private final MatchReportStageService stageService;

  public void processAndReport(
      List<MatchDomain> processedMatches, DbufInstanceConfigDomain config) {

    if (config.getDiscordChannelId() == null) {
      log.info("Discord channel ID is null, skipping match reporting");
      return;
    }

    List<ReportAnalyzer> sortedAnalyzers =
        enabledAnalyzers(config).sorted(Comparator.comparingInt(ReportAnalyzer::getOrder)).toList();

    Set<Long> focusPlayerIds = config.getPlayerIds();

    for (MatchDomain match : processedMatches) {
      try {
        MatchReportContext context = buildContext(match, config, focusPlayerIds);
        List<Report> allReports = runAnalyzers(context, sortedAnalyzers);
        sendToDiscord(match, config, context, allReports, sortedAnalyzers);
      } catch (Exception e) {
        log.error("Failed to report match {}: {}", match.getId(), e.getMessage(), e);
      }
    }
  }

  /**
   * Tells the channel that history was imported without per-match reports, so the silence after
   * registration is explained rather than mistaken for a failure.
   */
  public void sendBackfillNotice(DbufInstanceConfigDomain config, int imported) {
    if (config.getDiscordChannelId() == null || imported == 0) {
      return;
    }
    discordMessageService.sendChannelMessage(
        config.getDiscordChannelId(),
        "📥 Imported "
            + imported
            + " past matches for `/stats`. Live reports start with your next game.");
  }

  /** {@code threadId} is 0 when the header was posted but its thread could not be made. */
  public record PartialReportResult(long threadId, long headerMessageId) {
    public boolean hasThread() {
      return threadId > 0;
    }
  }

  /**
   * Phase 1: Send partial report with header + thread + partial-data analyzers. Returns the Discord
   * thread ID and header message ID for later use.
   */
  public PartialReportResult processAndReportPartial(
      MatchDomain match, DbufInstanceConfigDomain config) {
    return processAndReportPartial(match, config, NewMatchSummary.empty());
  }

  /**
   * Phase 1 with what the recent-matches list said of the match, which the staged header and card
   * are made of; channels without stages ignore it.
   */
  public PartialReportResult processAndReportPartial(
      MatchDomain match, DbufInstanceConfigDomain config, NewMatchSummary summary) {
    if (config.getDiscordChannelId() == null) {
      log.info("Discord channel ID is null, skipping partial report");
      return null;
    }
    if (stageService.enabled()) {
      return stageService.start(match, config, summary, buildPlayerNameMap(config));
    }

    Set<Long> focusPlayerIds = config.getPlayerIds();
    MatchReportContext context = buildContext(match, config, focusPlayerIds);

    List<ReportAnalyzer> partialAnalyzers =
        enabledAnalyzers(config)
            .filter(a -> a instanceof PartialDataAvailable)
            .filter(a -> ((PartialDataAvailable) a).isApplicable(context))
            .sorted(Comparator.comparingInt(ReportAnalyzer::getOrder))
            .toList();

    String header = headerRenderer.legacyWaiting(match.getId());

    Message headerMessage =
        discordMessageService.sendChannelMessage(config.getDiscordChannelId(), header);
    if (headerMessage == null) {
      log.warn("Failed to send header for match {}", match.getId());
      return null;
    }

    String threadName = "Match " + match.getId();
    ThreadChannel thread = discordMessageService.createThread(headerMessage, threadName);
    threadRecorder.remember(match, config, thread, headerMessage, null, null);

    List<Report> partialReports = runAnalyzers(context, partialAnalyzers);
    List<String> formatted =
        reportFormatter.formatForDiscord(partialReports, context.getPlayerNames());
    for (String message : formatted) {
      if (message != null && !message.isEmpty()) {
        discordMessageService.sendThreadMessageBlocking(thread, message);
      }
    }

    log.info("Partial report sent for match {} in thread {}", match.getId(), thread.getIdLong());
    return new PartialReportResult(thread.getIdLong(), headerMessage.getIdLong());
  }

  /** Phase 2: Send full-data reports to an existing Discord thread and update header. */
  public void processAndReportFull(
      MatchDomain match, DbufInstanceConfigDomain config, long threadId, long headerMessageId) {
    if (config.getDiscordChannelId() == null) {
      return;
    }

    Set<Long> focusPlayerIds = config.getPlayerIds();
    MatchReportContext context = buildContext(match, config, focusPlayerIds);

    // Update header with full match info
    if (headerMessageId > 0 && mayEditHeaderAsText(match.getId(), config.getId())) {
      String winLoss = determineWinLoss(context);
      String fullHeader = headerRenderer.legacyParsed(match, winLoss);
      discordMessageService.editMessage(config.getDiscordChannelId(), headerMessageId, fullHeader);
    }

    List<ReportAnalyzer> fullAnalyzers =
        enabledAnalyzers(config)
            .filter(a -> a instanceof FullDataAvailable)
            .filter(a -> ((FullDataAvailable) a).isApplicable(context))
            .sorted(Comparator.comparingInt(ReportAnalyzer::getOrder))
            .toList();

    List<Report> fullReports = runAnalyzers(context, fullAnalyzers);
    if (fullReports.isEmpty()) {
      log.debug("No full-data reports for match {}", match.getId());
      return;
    }

    ThreadChannel thread = discordMessageService.getThreadById(threadId);
    if (thread == null) {
      log.warn("Discord thread {} not found for match {}", threadId, match.getId());
      return;
    }

    List<String> formatted =
        reportFormatter.formatForDiscord(fullReports, context.getPlayerNames());
    for (String message : formatted) {
      if (message != null && !message.isEmpty()) {
        discordMessageService.sendThreadMessageBlocking(thread, message);
      }
    }

    log.info("Full report sent for match {} in thread {}", match.getId(), threadId);
  }

  /**
   * A plain header is edited as text. When the lookup failed the header is left alone, except with
   * stages off, where the old code always edited it.
   */
  private boolean mayEditHeaderAsText(long matchId, String instanceId) {
    HeaderKind kind = stageService.headerKind(matchId, instanceId);
    return kind == HeaderKind.PLAIN || (kind == HeaderKind.UNKNOWN && !stageService.enabled());
  }

  /** The analyzers this channel's settings allow. */
  private Stream<ReportAnalyzer> enabledAnalyzers(DbufInstanceConfigDomain config) {
    InstanceSettings settings = config.effectiveSettings();
    return analyzers.stream().filter(analyzer -> analyzer.isEnabledFor(settings));
  }

  public List<Report> analyzeMatch(MatchDomain match, DbufInstanceConfigDomain config) {
    Set<Long> focusPlayerIds = config.getPlayerIds();
    MatchReportContext context = buildContext(match, config, focusPlayerIds);

    List<ReportAnalyzer> sortedAnalyzers =
        analyzers.stream().sorted(Comparator.comparingInt(ReportAnalyzer::getOrder)).toList();

    return runAnalyzers(context, sortedAnalyzers);
  }

  private List<Report> runAnalyzers(
      MatchReportContext context, List<ReportAnalyzer> sortedAnalyzers) {
    List<Report> allReports = new ArrayList<>();

    for (ReportAnalyzer analyzer : sortedAnalyzers) {
      try {
        List<Report> reports = analyzer.analyze(context);
        if (reports != null) {
          allReports.addAll(reports);
        }
      } catch (Exception e) {
        log.error(
            "Analyzer {} failed for match {}: {}",
            analyzer.getAnalyzerName(),
            context.getMatch().getId(),
            e.getMessage(),
            e);
      }
    }

    return allReports;
  }

  private void sendToDiscord(
      MatchDomain match,
      DbufInstanceConfigDomain config,
      MatchReportContext context,
      List<Report> allReports,
      List<ReportAnalyzer> sortedAnalyzers) {

    String winLoss = determineWinLoss(context);
    String header = headerRenderer.legacyParsed(match, winLoss);

    Message headerMessage =
        discordMessageService.sendChannelMessage(config.getDiscordChannelId(), header);
    if (headerMessage == null) {
      log.warn(
          "Failed to send header message for match {} to channel {}",
          match.getId(),
          config.getDiscordChannelId());
      return;
    }

    String threadName = "Match " + match.getId();
    ThreadChannel thread = discordMessageService.createThread(headerMessage, threadName);
    threadRecorder.remember(match, config, thread, headerMessage, null, null);

    List<String> formatted = reportFormatter.formatForDiscord(allReports, context.getPlayerNames());
    for (String message : formatted) {
      if (message != null && !message.isEmpty()) {
        discordMessageService.sendThreadMessageBlocking(thread, message);
      }
    }
  }

  private MatchReportContext buildContext(
      MatchDomain match, DbufInstanceConfigDomain config, Set<Long> focusPlayerIds) {

    List<PlayerMatchStatisticDomain> playerStats =
        playerGameStatisticRepo.findAllByMatchId(match.getId());
    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(match.getId());
    List<ItemDomain> items = itemRepository.findAllByMatchId(match.getId());
    List<KillLogDomain> killLogs = killLogRepo.findAllByMatchId(match.getId());

    Map<Long, String> playerNames = buildPlayerNameMap(config);

    MatchReportContext context =
        MatchReportContext.builder()
            .match(match)
            .playerStatistics(playerStats)
            .abilities(abilities)
            .items(items)
            .killLogs(killLogs)
            .instanceConfig(config)
            .focusPlayerIds(focusPlayerIds)
            .playerNames(playerNames)
            .build();

    context.computeDerivedFields();
    return context;
  }

  private static String determineWinLoss(MatchReportContext context) {
    return MatchFacts.Result.ofStats(
            context.getPlayerStatistics().stream()
                .filter(s -> context.getFocusPlayerIds().contains(s.getPlayerId()))
                .toList())
        .label();
  }

  private Map<Long, String> buildPlayerNameMap(DbufInstanceConfigDomain config) {
    if (config.getPlayers() == null) {
      return Map.of();
    }
    return config.getPlayers().stream()
        .filter(p -> p.getId() != null)
        .collect(Collectors.toMap(PlayerDomain::getId, PlayerDomain::getName, (a, b) -> a));
  }
}
