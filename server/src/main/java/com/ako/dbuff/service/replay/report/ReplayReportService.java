package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayReportPostedRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.TextReport;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.metrics.ReplayMetrics;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

/**
 * Follows a match's report into its Discord thread with what the replay showed. It runs after the
 * replay pipeline and knows nothing of how that works: it looks for finished runs, and for each
 * channel that tracks a player of the match posts once, honouring the server switch, the channel's
 * own switch, its detail level and its quiet hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayReportService {
  private final ReplayReportProperties properties;
  private final ReplayProcessingProperties processing;
  private final ReplayParseRunRepo runRepo;
  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final DbufInstanceConfigRepository instanceRepo;
  private final MatchReportThreadRepo threadRepo;
  private final ReplayReportPostedRepo postedRepo;
  private final List<ReplayReportSection> sections;
  private final SectionSwitch sectionSwitch;
  private final ReportFormatter formatter;
  private final DiscordMessageService discord;
  private final QuietHoursGuard quietHours;
  private final GameModeRegistry modes;
  private final ReplayMetrics metrics;
  private final ComponentsViewRenderer componentsRenderer;

  /** Delivers what is waiting for the matches whose replay run finished lately. */
  public void deliverPending() {
    if (!properties.isEnabled()) {
      return;
    }
    LocalDateTime since = LocalDateTime.now().minusHours(properties.getLookbackHours());
    runRepo.findFinishedSince(processing.getParserVersion(), since).stream()
        .map(ReplayParseRunDomain::getMatchId)
        .distinct()
        .forEach(this::deliver);
  }

  /** Delivers the match's report if its run ended DONE or PARTIAL with no retry left. */
  public void deliverFinished(long matchId) {
    boolean complete =
        runRepo
            .findByMatchIdAndParserVersion(matchId, processing.getParserVersion())
            .filter(
                run ->
                    run.getStatus() == ReplayStatus.DONE
                        || (run.getStatus() == ReplayStatus.PARTIAL
                            && run.getNextAttemptAt() == null))
            .isPresent();
    if (complete) {
      deliver(matchId);
    }
  }

  public void deliver(long matchId) {
    if (!properties.isEnabled()) {
      return;
    }
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (match == null) {
      return;
    }
    List<PlayerMatchStatisticDomain> stats = statRepo.findAllByMatchId(matchId);
    for (DbufInstanceConfigDomain instance : instancesTracking(stats)) {
      try {
        deliverTo(match, stats, instance);
      } catch (RuntimeException e) {
        log.warn("Replay report of match {} for instance {} failed", matchId, instance.getId(), e);
      }
    }
  }

  private Iterable<DbufInstanceConfigDomain> instancesTracking(
      List<PlayerMatchStatisticDomain> stats) {
    Map<String, DbufInstanceConfigDomain> byId = new LinkedHashMap<>();
    stats.stream()
        .map(PlayerMatchStatisticDomain::getPlayerId)
        .filter(id -> id != null && id > 0)
        .distinct()
        .forEach(
            id ->
                instanceRepo.findActiveByPlayerId(id).forEach(i -> byId.putIfAbsent(i.getId(), i)));
    return byId.values();
  }

  private void deliverTo(
      MatchDomain match,
      List<PlayerMatchStatisticDomain> stats,
      DbufInstanceConfigDomain instance) {
    InstanceSettings settings = instance.effectiveSettings();
    if (instance.getDiscordChannelId() == null
        || !settings.isReplayReportOn()
        || settings.detail() == ReportDetail.MINIMAL
        || postedRepo.findByMatchIdAndInstanceId(match.getId(), instance.getId()).isPresent()
        || quietHours.isQuietTime(settings)) {
      return;
    }
    MatchReportThreadDomain thread =
        threadRepo.findByMatchIdAndInstanceId(match.getId(), instance.getId()).orElse(null);
    if (thread == null) {
      log.debug(
          "No thread of match {} in instance {}, nothing to follow up",
          match.getId(),
          instance.getId());
      return;
    }
    List<ReportMessage> messages = messages(match, stats, instance, settings);
    if (messages.isEmpty()) {
      return;
    }
    ThreadChannel channel = discord.getThreadById(thread.getThreadId());
    if (channel == null) {
      log.debug(
          "Discord thread {} of match {} not found, will try again",
          thread.getThreadId(),
          match.getId());
      return;
    }
    postedRepo.save(
        ReplayReportPostedDomain.builder()
            .matchId(match.getId())
            .instanceId(instance.getId())
            .messages(messages.size())
            .postedAt(LocalDateTime.now())
            .build());
    messages.forEach(message -> send(channel, message));
    metrics.reportPosted();
    log.info("Replay report of match {} posted in {} messages", match.getId(), messages.size());
  }

  private void send(ThreadChannel channel, ReportMessage message) {
    switch (message) {
      case ReportMessage.Plain plain -> discord.sendThreadMessageBlocking(channel, plain.text());
      case ReportMessage.Components cards ->
          discord.sendThreadComponentsBlocking(channel, cards.components());
    }
  }

  private List<ReportMessage> messages(
      MatchDomain match,
      List<PlayerMatchStatisticDomain> stats,
      DbufInstanceConfigDomain instance,
      InstanceSettings settings) {
    GameModeProfile mode = modes.activeFor(match.getGameModeId()).orElse(null);
    if (mode == null) {
      return List.of();
    }
    if (sections.stream().noneMatch(sectionSwitch::isEnabled)) {
      log.info(
          "Replay report of match {} not posted: every section is switched off", match.getId());
      return List.of();
    }
    ReplayReportInput input = input(match, stats, instance);
    List<ReplayReportSection> active =
        sections.stream()
            .filter(sectionSwitch::isEnabled)
            .filter(s -> s.supports(mode))
            .filter(s -> settings.detail().includes(s.minimumDetail()))
            .sorted(Comparator.comparingInt(ReplayReportSection::order))
            .toList();
    List<ReportMessage> all =
        properties.getFormat() == ReportFormat.COMPONENTS
            ? components(active, input)
            : plain(active, input);
    int cap =
        Math.max(
            1,
            settings.detail() == ReportDetail.FULL
                ? properties.getMaxMessages()
                : properties.getStandardMaxMessages());
    return MessageCap.apply(all, cap);
  }

  private List<ReportMessage> plain(List<ReplayReportSection> active, ReplayReportInput input) {
    List<Report> reports = new ArrayList<>();
    for (ReplayReportSection section : active) {
      List<String> lines = section.lines(input);
      if (!lines.isEmpty()) {
        reports.add(new TextReport("replay", String.join("\n", lines)));
      }
    }
    if (reports.isEmpty()) {
      return List.of();
    }
    return formatter.formatForDiscord(reports, Map.of()).stream()
        .<ReportMessage>map(ReportMessage.Plain::new)
        .toList();
  }

  private List<ReportMessage> components(
      List<ReplayReportSection> active, ReplayReportInput input) {
    return active.stream()
        .flatMap(section -> section.view(input).stream())
        .flatMap(view -> componentsRenderer.render(view).stream())
        .toList();
  }

  private ReplayReportInput input(
      MatchDomain match,
      List<PlayerMatchStatisticDomain> stats,
      DbufInstanceConfigDomain instance) {
    Set<Long> focusIds = instance.getPlayerIds();
    Map<Long, String> names =
        instance.getPlayers() == null
            ? Map.of()
            : instance.getPlayers().stream()
                .filter(p -> p.getId() != null)
                .collect(
                    Collectors.toMap(
                        PlayerDomain::getId, p -> String.valueOf(p.getName()), (a, b) -> a));
    Map<Long, String> namesBySlot = new HashMap<>();
    stats.stream()
        .filter(s -> focusIds.contains(s.getPlayerId()))
        .forEach(
            s -> namesBySlot.put(s.getPlayerSlot(), names.getOrDefault(s.getPlayerId(), "Player")));
    return new ReplayReportInput(
        match.getId(),
        match,
        Set.copyOf(namesBySlot.keySet()),
        namesBySlot,
        properties.getMaxFindingsPerSection());
  }
}
