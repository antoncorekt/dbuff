package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.replay.ReplayForecast;
import com.ako.dbuff.service.replay.report.view.ComponentsFallback;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;

/**
 * The staged life of a match thread: a header created when the match is first seen, with a basic
 * card of the tracked players, and retitled when OpenDota parses the match or gives up. Nothing
 * here throws; a header that cannot be reached is logged and the match goes on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchReportStageService {
  private final ReportStagesProperties properties;
  private final MatchFactsFactory factsFactory;
  private final HeaderRenderer headers;
  private final ReportHeaderPublisher publisher;
  private final StageOneCardBuilder cardBuilder;
  private final ComponentsViewRenderer cardRenderer;
  private final DiscordMessageService discord;
  private final MatchThreadRecorder recorder;
  private final ReplayForecast forecast;
  private final ReplayStageTargets targets;
  private final MatchRepo matchRepo;

  public boolean enabled() {
    return properties.isEnabled();
  }

  /** How the header of a match was posted, which decides how it may be edited. */
  public enum HeaderKind {
    /** A Components V2 message of the stages, edited only as components. */
    STAGED,
    /** Plain text, or no row at all, as before stages existed: edited only as text. */
    PLAIN,
    /** Not known, because the lookup failed: leave the header alone. */
    UNKNOWN
  }

  /**
   * Which kind of header the match has. That depends on the row alone, not on the switch: the
   * switch only decides how new headers are made. A row without a stage, including a header that
   * fell back to plain text, is plain.
   */
  public HeaderKind headerKind(long matchId, String instanceId) {
    if (instanceId == null) {
      return HeaderKind.PLAIN;
    }
    try {
      return recorder
          .find(matchId, instanceId)
          .map(row -> ownsHeader(row) ? HeaderKind.STAGED : HeaderKind.PLAIN)
          .orElse(HeaderKind.PLAIN);
    } catch (RuntimeException e) {
      log.warn("Could not look up the thread of match {}: {}", matchId, e.getMessage());
      return HeaderKind.UNKNOWN;
    }
  }

  private boolean ownsHeader(MatchReportThreadDomain row) {
    return row.getStage() != null;
  }

  /**
   * Stage 1: header, thread and card. Null when the header could not be posted; the thread id is 0
   * when the header exists but its thread could not be made.
   */
  public PartialReportResult start(
      MatchDomain match,
      DbufInstanceConfigDomain config,
      NewMatchSummary summary,
      Map<Long, String> playerNames) {
    ReportHeaderPublisher.Posted header;
    MatchFacts facts;
    try {
      facts = factsFactory.fromSummary(match, config, summary);
      header =
          publisher.create(
              config.getDiscordChannelId(),
              headers.render(StageTarget.at(MatchReportStage.REQUESTED), facts));
    } catch (RuntimeException e) {
      log.warn("Stage 1 of match {} failed: {}", match.getId(), e.getMessage(), e);
      return null;
    }
    if (header == null) {
      log.warn("Failed to send header for match {}", match.getId());
      return null;
    }
    long headerId = header.message().getIdLong();
    // A header that fell back to plain text has no stage: it is edited as text, never as V2.
    MatchReportStage stage = header.components() ? MatchReportStage.REQUESTED : null;
    ThreadChannel thread = null;
    try {
      thread = discord.createThread(header.message(), "Match " + match.getId());
    } catch (RuntimeException e) {
      log.warn(
          "Stage 1 of match {} went on without a thread: {}", match.getId(), e.getMessage(), e);
    }
    recorder.remember(match, config, thread, header.message(), stage, facts);
    if (thread == null) {
      return new PartialReportResult(0L, headerId);
    }
    try {
      postCard(thread, cardBuilder.build(facts, summary.lines(), playerNames));
    } catch (RuntimeException e) {
      log.warn("No player card in thread {}: {}", thread.getIdLong(), e.getMessage(), e);
    }
    log.info("Stage 1 sent for match {} in thread {}", match.getId(), thread.getIdLong());
    return new PartialReportResult(thread.getIdLong(), headerId);
  }

  /** Stage 2: OpenDota parsed the match; the header says whether a replay analysis follows. */
  public void parsed(MatchDomain match, DbufInstanceConfigDomain config) {
    try {
      recorder
          .find(match.getId(), config.getId())
          .filter(this::ownsHeader)
          .ifPresent(row -> retitleParsed(row, match, config));
    } catch (RuntimeException e) {
      log.warn("Stage 2 of match {} failed: {}", match.getId(), e.getMessage(), e);
    }
  }

  private void retitleParsed(
      MatchReportThreadDomain row, MatchDomain match, DbufInstanceConfigDomain config) {
    MatchReportStage stage =
        forecast.isExpected(match) ? MatchReportStage.REPLAY_PENDING : MatchReportStage.PARSED;
    retitle(row, stage, factsFactory.fromParsed(match, config).orFrom(row));
  }

  /** OpenDota never parsed the match; the header keeps what stage 1 said and states that. */
  public void parseTimedOut(MatchDomain match, String instanceId) {
    if (instanceId == null) {
      return;
    }
    try {
      recorder
          .find(match.getId(), instanceId)
          .ifPresentOrElse(
              row -> timedOut(row, match),
              () -> log.debug("No thread of match {} in instance {}", match.getId(), instanceId));
    } catch (RuntimeException e) {
      log.warn("Timeout title of match {} failed: {}", match.getId(), e.getMessage(), e);
    }
  }

  private void timedOut(MatchReportThreadDomain row, MatchDomain match) {
    MatchFacts facts = factsFactory.fromRow(row, match);
    if (ownsHeader(row)) {
      retitle(row, MatchReportStage.PARSE_TIMED_OUT, facts);
    } else if (properties.isEnabled()
        && row.getHeaderMessageId() != null
        && row.getChannelId() != null) {
      editPlain(row, headers.title(StageTarget.at(MatchReportStage.PARSE_TIMED_OUT), facts));
    }
  }

  private void editPlain(MatchReportThreadDomain row, String title) {
    try {
      discord.editMessage(row.getChannelId(), row.getHeaderMessageId(), title);
    } catch (RuntimeException e) {
      log.warn("Could not edit header message {}: {}", row.getHeaderMessageId(), e.getMessage());
    }
  }

  private void retitle(MatchReportThreadDomain row, MatchReportStage stage, MatchFacts facts) {
    retitle(row, StageTarget.at(stage), facts, false);
  }

  /**
   * The stored stage moves only forward, and only once Discord shows the new title; {@code force}
   * lifts the first rule for a deliberate refresh.
   */
  private boolean retitle(
      MatchReportThreadDomain row, StageTarget target, MatchFacts facts, boolean force) {
    MatchReportStage stage = target.stage();
    if (!force && !stage.mayFollow(row.getStage())) {
      log.debug("Match {} stays at {}, not {}", row.getMatchId(), row.getStage(), stage);
      return false;
    }
    if (row.getHeaderMessageId() == null || row.getChannelId() == null) {
      log.debug("Match {} has no header message to retitle", row.getMatchId());
      return false;
    }
    if (!publisher.edit(
        row.getChannelId(), row.getHeaderMessageId(), headers.render(target, facts))) {
      return false;
    }
    if (force) {
      recorder.force(row, stage, facts);
    } else {
      recorder.advance(row, stage, facts);
    }
    return true;
  }

  /**
   * A replay run reached {@code status}: when that is final, every staged thread of the match is
   * retitled, whatever the report switches say. A legacy plain header is never touched.
   */
  public void replayFinished(long matchId, ReplayStatus status, boolean retryLeft) {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      StageTarget.of(status, retryLeft).ifPresent(target -> retitleAll(matchId, target));
    } catch (RuntimeException e) {
      log.warn("Replay title of match {} failed: {}", matchId, e.getMessage(), e);
    }
  }

  private void retitleAll(long matchId, StageTarget target) {
    List<MatchReportThreadDomain> rows = recorder.findAll(matchId);
    if (rows.isEmpty()) {
      return;
    }
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    for (MatchReportThreadDomain row : rows) {
      try {
        if (ownsHeader(row)) {
          retitle(row, target, factsFactory.fromRow(row, match), false);
        }
      } catch (RuntimeException e) {
        log.warn(
            "Replay title of match {} in {} failed: {}",
            matchId,
            row.getInstanceId(),
            e.getMessage());
      }
    }
  }

  /**
   * Retitles the header from what is stored now, even to a stage the header has passed (a rerun).
   * Only a staged header is touched.
   *
   * @return whether the header was changed
   */
  public boolean refresh(MatchDomain match, DbufInstanceConfigDomain instance) {
    if (!properties.isEnabled()) {
      return false;
    }
    try {
      Optional<MatchReportThreadDomain> row =
          recorder.find(match.getId(), instance.getId()).filter(this::ownsHeader);
      Optional<StageTarget> target = row.isPresent() ? targets.forMatch(match) : Optional.empty();
      if (target.isEmpty()) {
        return false;
      }
      MatchFacts facts = factsFactory.fromParsed(match, instance).orFrom(row.get());
      return retitle(row.get(), target.get(), facts, true);
    } catch (RuntimeException e) {
      log.warn("Header refresh of match {} failed: {}", match.getId(), e.getMessage(), e);
      return false;
    }
  }

  private void postCard(ThreadChannel thread, SectionView card) {
    if (card == null) {
      return;
    }
    for (ReportMessage message : cardRenderer.render(card)) {
      if (message instanceof ReportMessage.Components cards) {
        try {
          send(thread, cards);
        } catch (RuntimeException e) {
          log.warn(
              "Could not post the player card in thread {}: {}",
              thread.getIdLong(),
              e.getMessage());
        }
      }
    }
  }

  private void send(ThreadChannel thread, ReportMessage.Components cards) {
    try {
      discord.sendThreadComponentsBlocking(thread, cards.components());
    } catch (RuntimeException e) {
      if (!ComponentsFallback.payloadRejected(e)) {
        throw e;
      }
      log.warn("Player card components rejected, posting plain text: {}", e.getMessage());
      String text = cards.fallback();
      discord.sendThreadMessageBlocking(
          thread, text.substring(0, Math.min(text.length(), MessageLimits.PLAIN_TEXT)));
    }
  }
}
