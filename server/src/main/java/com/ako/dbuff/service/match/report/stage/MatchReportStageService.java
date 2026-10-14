package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.replay.ReplayForecast;
import com.ako.dbuff.service.replay.report.view.ComponentsFallback;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.MessageLimits;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.Map;
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

  public boolean enabled() {
    return properties.isEnabled();
  }

  /**
   * Whether the header of the match is a staged Components V2 message this service retitles. That
   * depends on the row alone, not on the switch: the switch only decides how new headers are made.
   * A row without a stage, including a header that fell back to plain text, is only ever edited as
   * text.
   */
  public boolean ownsHeader(long matchId, String instanceId) {
    if (instanceId == null) {
      return false;
    }
    try {
      return recorder.find(matchId, instanceId).map(this::ownsHeader).orElse(false);
    } catch (RuntimeException e) {
      log.warn("Could not look up the thread of match {}: {}", matchId, e.getMessage());
      return false;
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
      facts = factsFactory.fromSummary(match.getId(), summary);
      header =
          publisher.create(
              config.getDiscordChannelId(), headers.render(MatchReportStage.REQUESTED, facts));
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
      MatchReportStage stage =
          forecast.isExpected(match) ? MatchReportStage.REPLAY_PENDING : MatchReportStage.PARSED;
      recorder
          .find(match.getId(), config.getId())
          .filter(this::ownsHeader)
          .ifPresentOrElse(
              row -> retitle(row, stage, factsFactory.fromParsed(match, config)),
              () ->
                  log.debug(
                      "No staged thread of match {} in instance {}",
                      match.getId(),
                      config.getId()));
    } catch (RuntimeException e) {
      log.warn("Stage 2 of match {} failed: {}", match.getId(), e.getMessage(), e);
    }
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
    MatchFacts facts = MatchFacts.fromRow(row, match);
    if (ownsHeader(row)) {
      retitle(row, MatchReportStage.PARSE_TIMED_OUT, facts);
    } else if (row.getHeaderMessageId() != null && row.getChannelId() != null) {
      editPlain(row, headers.title(MatchReportStage.PARSE_TIMED_OUT, facts));
    }
  }

  private void editPlain(MatchReportThreadDomain row, String title) {
    try {
      discord.editMessage(row.getChannelId(), row.getHeaderMessageId(), title);
    } catch (RuntimeException e) {
      log.warn("Could not edit header message {}: {}", row.getHeaderMessageId(), e.getMessage());
    }
  }

  /** The stored stage moves only forward, and only once Discord shows the new title. */
  private void retitle(MatchReportThreadDomain row, MatchReportStage stage, MatchFacts facts) {
    if (!stage.mayFollow(row.getStage())) {
      log.debug("Match {} stays at {}, not {}", row.getMatchId(), row.getStage(), stage);
      return;
    }
    if (row.getHeaderMessageId() == null || row.getChannelId() == null) {
      log.debug("Match {} has no header message to retitle", row.getMatchId());
      return;
    }
    if (publisher.edit(
        row.getChannelId(), row.getHeaderMessageId(), headers.render(stage, facts))) {
      recorder.advance(row, stage, facts);
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
