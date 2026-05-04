package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
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
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchReportOrchestrator {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final DiscordMessageService discordMessageService;
  private final List<ReportAnalyzer> analyzers;
  private final ReportFormatter reportFormatter;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final AbilityRepo abilityRepo;
  private final ItemRepository itemRepository;
  private final KillLogRepo killLogRepo;

  public void processAndReport(
      List<MatchDomain> processedMatches, DbufInstanceConfigDomain config) {

    if (config.getDiscordChannelId() == null) {
      log.info("Discord channel ID is null, skipping match reporting");
      return;
    }

    List<ReportAnalyzer> sortedAnalyzers =
        analyzers.stream().sorted(Comparator.comparingInt(ReportAnalyzer::getOrder)).toList();

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
    String header = buildHeader(match, winLoss);

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

  private String buildHeader(MatchDomain match, String winLoss) {
    StringBuilder sb = new StringBuilder();
    sb.append("Match ").append(match.getId());

    if (match.getGameModeName() != null) {
      sb.append(" - ").append(match.getGameModeName());
    }

    sb.append(" - ").append(winLoss);

    if (match.getRadiantScore() != null && match.getDireScore() != null) {
      sb.append(" (")
          .append(match.getRadiantScore())
          .append("-")
          .append(match.getDireScore())
          .append(")");
    }

    if (match.getStartTime() != null) {
      String date =
          Instant.ofEpochSecond(match.getStartTime())
              .atZone(ZoneId.systemDefault())
              .format(DATE_FORMATTER);
      sb.append("; match date - ").append(date);
    } else if (match.getStartLocalDate() != null) {
      sb.append("; match date - ").append(match.getStartLocalDate());
    }

    if (match.getDuration() != null) {
      long minutes = match.getDuration() / 60;
      sb.append(" with duration ").append(minutes).append(" min");
    }

    return sb.toString();
  }

  private static String determineWinLoss(MatchReportContext context) {
    List<PlayerMatchStatisticDomain> focusStats =
        context.getPlayerStatistics().stream()
            .filter(s -> context.getFocusPlayerIds().contains(s.getPlayerId()))
            .toList();

    if (focusStats.isEmpty()) {
      return "Unknown";
    }

    long wins = focusStats.stream().filter(s -> s.getWin() != null && s.getWin() == 1).count();
    long losses = focusStats.size() - wins;

    if (wins > 0 && losses == 0) {
      return "Win";
    } else if (losses > 0 && wins == 0) {
      return "Loss";
    }
    return "Mixed (" + wins + "W/" + losses + "L)";
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
