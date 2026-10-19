package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayReportPostedRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.discord.icon.IconText;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.TextReport;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.metrics.ReplayMetrics;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.report.view.ComponentsFallback;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.ImageAttachment;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import com.ako.dbuff.service.scheduler.QuietHoursGuard;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

/**
 * Follows a match's report into its Discord thread with what the replay showed. It runs after the
 * replay pipeline and knows nothing of how that works: it looks for finished runs, and for each
 * channel that tracks a player of the match posts once (unless asked again, see {@link
 * #redeliver}), honouring the server switch, the channel's own switch, its detail level and its
 * quiet hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayReportService {
  /** How a page of a multi-page section reads as text; it holds no state. */
  private static final PlainViewRenderer PAGE_TEXT = new PlainViewRenderer();

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
  private final ReportHeroNames heroNames;

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
            .filter(ReplayParseRunDomain::finalWithAnalysis)
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
        deliverTo(match, stats, instance, Trigger.AUTOMATIC);
      } catch (RuntimeException e) {
        log.warn("Replay report of match {} for instance {} failed", matchId, instance.getId(), e);
      }
    }
  }

  /**
   * Posts the report of a match with a stored replay (DONE or PARTIAL) into one channel's thread
   * again, for a person who asked for it. Skips the already-posted check and the quiet hours; the
   * server switch, the channel's own switch, its detail level and the section switches still apply.
   */
  public ReportDelivery redeliver(long matchId, String instanceId) {
    if (!properties.isEnabled()) {
      return logSkipped(matchId, ReportDelivery.REPORT_SWITCHED_OFF);
    }
    try {
      if (!hasStoredAnalysis(matchId)) {
        return logSkipped(matchId, ReportDelivery.NOT_STORED);
      }
      MatchDomain match = matchRepo.findById(matchId).orElse(null);
      DbufInstanceConfigDomain instance = instanceRepo.findById(instanceId).orElse(null);
      if (match == null || instance == null) {
        return logSkipped(matchId, ReportDelivery.NO_INSTANCE);
      }
      return logSkipped(
          matchId,
          deliverTo(match, statRepo.findAllByMatchId(matchId), instance, Trigger.REQUESTED));
    } catch (RuntimeException e) {
      log.warn("Replay report of match {} for instance {} failed", matchId, instanceId, e);
      return ReportDelivery.FAILED;
    }
  }

  /** Whether the match has a stored replay analysis, a run that ended DONE or PARTIAL. */
  public boolean hasStoredAnalysis(long matchId) {
    return runRepo
        .findByMatchIdAndParserVersion(matchId, processing.getParserVersion())
        .filter(run -> run.getStatus().hasAnalysis())
        .isPresent();
  }

  /**
   * Posts one section of a match with a stored replay into one channel's thread for a person who
   * asked for it, in the report's format. Only the section's own output decides; the report switch,
   * the section lists, the detail level and the quiet hours do not apply, so a section that is off
   * for automatic posting can still be asked for. A section hidden by its kill switch is refused.
   */
  public ReportDelivery deliverSection(long matchId, String instanceId, String sectionName) {
    return deliverSection(matchId, instanceId, sectionName, null);
  }

  /**
   * The same, with an AI model a person chose for this request; the AI sections ask it instead of
   * the default one.
   */
  public ReportDelivery deliverSection(
      long matchId, String instanceId, String sectionName, String modelOverride) {
    return deliverSection(matchId, instanceId, sectionName, modelOverride, null);
  }

  /**
   * The same, with the item categories or entry keys a person chose for this request; the items
   * section lists those instead of the channel's choice.
   */
  public ReportDelivery deliverSection(
      long matchId,
      String instanceId,
      String sectionName,
      String modelOverride,
      List<String> itemFilter) {
    try {
      if (!hasStoredAnalysis(matchId)) {
        return logSkipped(matchId, ReportDelivery.NOT_STORED);
      }
      ReplayReportSection section =
          sections.stream().filter(s -> s.name().equals(sectionName)).findFirst().orElse(null);
      MatchDomain match = matchRepo.findById(matchId).orElse(null);
      DbufInstanceConfigDomain instance = instanceRepo.findById(instanceId).orElse(null);
      if (section == null || match == null || instance == null) {
        return logSkipped(matchId, ReportDelivery.NO_INSTANCE);
      }
      if (sectionSwitch.isHidden(section)) {
        return logSkipped(matchId, ReportDelivery.SECTION_HIDDEN);
      }
      GameModeProfile mode = modes.activeFor(match.getGameModeId()).orElse(null);
      if (mode != null && !section.supports(mode)) {
        return logSkipped(matchId, ReportDelivery.SECTION_NOT_FOR_MODE);
      }
      List<PlayerMatchStatisticDomain> stats = statRepo.findAllByMatchId(matchId);
      return logSkipped(
          matchId,
          postToThread(
              match,
              instance,
              () ->
                  render(
                      List.of(section),
                      input(match, stats, instance)
                          .withModelOverride(modelOverride)
                          .withItemFilter(itemFilter)),
              ReportDelivery.SECTION_EMPTY,
              false));
    } catch (RuntimeException e) {
      log.warn("Section {} of match {} failed: {}", sectionName, matchId, e.getMessage(), e);
      return ReportDelivery.FAILED;
    }
  }

  private ReportDelivery logSkipped(long matchId, ReportDelivery result) {
    if (result == ReportDelivery.THREAD_NOT_FOUND) {
      log.warn("Replay report of match {} not posted: {}", matchId, result.reason());
    } else if (result != ReportDelivery.POSTED) {
      log.debug("Replay report of match {} not posted: {}", matchId, result.reason());
    }
    return result;
  }

  /** Who asked: an automatic delivery posts once and waits out quiet hours; a person does not. */
  private enum Trigger {
    AUTOMATIC,
    REQUESTED
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

  private ReportDelivery deliverTo(
      MatchDomain match,
      List<PlayerMatchStatisticDomain> stats,
      DbufInstanceConfigDomain instance,
      Trigger trigger) {
    InstanceSettings settings = instance.effectiveSettings();
    if (instance.getDiscordChannelId() == null) {
      return ReportDelivery.NO_CHANNEL;
    }
    if (!settings.isReplayReportOn()) {
      return ReportDelivery.REPORT_OFF_FOR_CHANNEL;
    }
    if (settings.detail() == ReportDetail.MINIMAL) {
      return ReportDelivery.MINIMAL_DETAIL;
    }
    if (trigger == Trigger.AUTOMATIC) {
      if (postedRepo.findByMatchIdAndInstanceId(match.getId(), instance.getId()).isPresent()) {
        return ReportDelivery.ALREADY_POSTED;
      }
      if (quietHours.isQuietTime(settings)) {
        return ReportDelivery.QUIET_HOURS;
      }
    }
    return postToThread(
        match,
        instance,
        () -> messages(match, stats, instance, settings),
        ReportDelivery.NO_SECTIONS,
        true);
  }

  private ReportDelivery postToThread(
      MatchDomain match,
      DbufInstanceConfigDomain instance,
      Supplier<List<ReportMessage>> build,
      ReportDelivery whenEmpty,
      boolean markPosted) {
    MatchReportThreadDomain thread =
        threadRepo.findByMatchIdAndInstanceId(match.getId(), instance.getId()).orElse(null);
    if (thread == null || thread.getThreadId() == null) {
      log.debug(
          "No thread of match {} in instance {}, nothing to follow up",
          match.getId(),
          instance.getId());
      return ReportDelivery.NO_THREAD;
    }
    List<ReportMessage> messages = build.get();
    if (messages.isEmpty()) {
      return whenEmpty;
    }
    ThreadChannel channel = discord.getThreadById(thread.getThreadId());
    if (channel == null) {
      log.debug(
          "Discord thread {} of match {} not found, will try again",
          thread.getThreadId(),
          match.getId());
      return ReportDelivery.THREAD_NOT_FOUND;
    }
    if (markPosted) {
      recordPosted(match.getId(), instance.getId(), messages.size());
    }
    messages.forEach(message -> send(match.getId(), channel, message));
    metrics.reportPosted();
    log.info("Replay report of match {} posted in {} messages", match.getId(), messages.size());
    return ReportDelivery.POSTED;
  }

  private void recordPosted(long matchId, String instanceId, int messageCount) {
    ReplayReportPostedDomain posted =
        postedRepo
            .findByMatchIdAndInstanceId(matchId, instanceId)
            .orElseGet(
                () ->
                    ReplayReportPostedDomain.builder()
                        .matchId(matchId)
                        .instanceId(instanceId)
                        .build());
    posted.setMessages(messageCount);
    posted.setPostedAt(LocalDateTime.now());
    postedRepo.save(posted);
  }

  private void send(long matchId, ThreadChannel channel, ReportMessage message) {
    switch (message) {
      case ReportMessage.Plain plain -> sendPlain(channel, plain.text(), plain.image());
      case ReportMessage.Components cards -> {
        try {
          if (cards.images().isEmpty()) {
            discord.sendThreadComponentsBlocking(channel, cards.components());
          } else {
            discord.sendThreadComponentsBlocking(
                channel,
                cards.components(),
                cards.images().stream().map(ImageAttachment::upload).toList());
          }
        } catch (RuntimeException e) {
          if (!ComponentsFallback.payloadRejected(e)) {
            throw e;
          }
          log.warn(
              "Components of the replay report of match {} rejected, posting plain text: {}",
              matchId,
              e.getMessage());
          plainTexts(cards.fallback()).forEach(t -> discord.sendThreadMessageBlocking(channel, t));
          cards
              .images()
              .forEach(
                  image -> discord.sendThreadMessageBlocking(channel, "", List.of(image.upload())));
        }
      }
    }
  }

  private void sendPlain(ThreadChannel channel, String text, ImageAttachment image) {
    if (image == null) {
      discord.sendThreadMessageBlocking(channel, text);
    } else {
      discord.sendThreadMessageBlocking(channel, text, List.of(image.upload()));
    }
  }

  private List<String> plainTexts(String text) {
    String cut =
        text.lines()
            .map(
                l ->
                    l.length() <= MessageLimits.PLAIN_TEXT
                        ? l
                        : l.substring(0, IconText.safeEnd(l, MessageLimits.PLAIN_TEXT - 1)) + "…")
            .collect(Collectors.joining("\n"));
    return formatter.formatForDiscord(List.of(new TextReport("replay", cut)), Map.of());
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
    if (sections.stream().noneMatch(s -> sectionSwitch.isAutomatic(s, settings))) {
      log.info(
          "Replay report of match {} not posted: every section is switched off", match.getId());
      return List.of();
    }
    ReplayReportInput input = input(match, stats, instance);
    List<ReplayReportSection> active =
        sections.stream()
            .filter(s -> sectionSwitch.isAutomatic(s, settings))
            .filter(s -> s.supports(mode))
            .filter(s -> settings.detail().includes(s.minimumDetail()))
            .sorted(Comparator.comparingInt(ReplayReportSection::order))
            .toList();
    List<ReportMessage> all = render(active, input);
    int cap =
        Math.max(
            1,
            settings.detail() == ReportDetail.FULL
                ? properties.getMaxMessages()
                : properties.getStandardMaxMessages());
    return MessageCap.apply(all, cap);
  }

  private List<ReportMessage> render(List<ReplayReportSection> active, ReplayReportInput input) {
    List<ReportMessage> all = new ArrayList<>();
    List<ReplayReportSection> batch = new ArrayList<>();
    for (ReplayReportSection section : active) {
      if (!section.postsOwnMessages()) {
        batch.add(section);
        continue;
      }
      all.addAll(renderBatch(batch, input));
      batch.clear();
      section.lines(input).forEach(text -> all.add(new ReportMessage.Plain(text)));
    }
    all.addAll(renderBatch(batch, input));
    return all;
  }

  private List<ReportMessage> renderBatch(
      List<ReplayReportSection> batch, ReplayReportInput input) {
    if (batch.isEmpty()) {
      return List.of();
    }
    return properties.getFormat() == ReportFormat.COMPONENTS
        ? components(batch, input)
        : plain(batch, input);
  }

  private List<ReportMessage> plain(List<ReplayReportSection> active, ReplayReportInput input) {
    List<ReportMessage> messages = new ArrayList<>();
    List<Report> reports = new ArrayList<>();
    for (ReplayReportSection section : active) {
      List<SectionView> pages = section.paged() ? section.pages(input) : List.of();
      if (pages.size() > 1) {
        messages.addAll(formatted(reports));
        reports.clear();
        pages.forEach(
            page -> messages.addAll(withImage(PAGE_TEXT.lines(page, true), page.image())));
        continue;
      }
      List<String> lines = section.lines(input);
      Optional<ImageAttachment> image = section.image(input);
      if (image.isPresent()) {
        messages.addAll(formatted(reports));
        reports.clear();
        messages.addAll(withImage(lines, image.get()));
      } else if (!lines.isEmpty()) {
        reports.add(new TextReport("replay", String.join("\n", lines)));
      }
    }
    messages.addAll(formatted(reports));
    return messages;
  }

  /** The section's text split to the message limit, the image on the last message. */
  private List<ReportMessage> withImage(List<String> lines, ImageAttachment image) {
    if (image == null) {
      return formatted(
          lines.isEmpty()
              ? List.of()
              : List.of(new TextReport("replay", String.join("\n", lines))));
    }
    List<String> texts =
        lines.isEmpty()
            ? List.of()
            : formatter.formatForDiscord(
                List.of(new TextReport("replay", String.join("\n", lines))), Map.of());
    if (texts.isEmpty()) {
      return List.of(new ReportMessage.Plain("", image));
    }
    List<ReportMessage> messages = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      messages.add(new ReportMessage.Plain(texts.get(i), i == texts.size() - 1 ? image : null));
    }
    return messages;
  }

  private List<ReportMessage> formatted(List<Report> reports) {
    if (reports.isEmpty()) {
      return List.of();
    }
    return formatter.formatForDiscord(reports, Map.of()).stream()
        .<ReportMessage>map(ReportMessage.Plain::new)
        .toList();
  }

  private List<ReportMessage> components(
      List<ReplayReportSection> active, ReplayReportInput input) {
    return componentsRenderer.renderAll(
        active.stream().flatMap(section -> section.pages(input).stream()).toList());
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
    Map<Long, String> heroes = heroNames.iconBySlot(match.getId());
    Map<Long, String> namesBySlot = new HashMap<>();
    stats.stream()
        .filter(s -> focusIds.contains(s.getPlayerId()))
        .forEach(
            s ->
                namesBySlot.put(
                    s.getPlayerSlot(),
                    ReportHeroNames.label(
                        names.getOrDefault(s.getPlayerId(), "Player"),
                        heroes.get(s.getPlayerSlot()))));
    return new ReplayReportInput(
            match.getId(),
            match,
            Set.copyOf(namesBySlot.keySet()),
            namesBySlot,
            properties.getMaxFindingsPerSection())
        .withInstance(instance);
  }
}
