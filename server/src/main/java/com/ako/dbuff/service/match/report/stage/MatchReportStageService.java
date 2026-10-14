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
import net.dv8tion.jda.api.entities.Message;
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

  /** Stage 1: header, thread and card. Null when the header could not be posted. */
  public PartialReportResult start(
      MatchDomain match,
      DbufInstanceConfigDomain config,
      NewMatchSummary summary,
      Map<Long, String> playerNames) {
    try {
      MatchFacts facts = factsFactory.fromSummary(match.getId(), summary);
      Message header =
          publisher.create(
              config.getDiscordChannelId(), headers.render(MatchReportStage.REQUESTED, facts));
      if (header == null) {
        log.warn("Failed to send header for match {}", match.getId());
        return null;
      }
      ThreadChannel thread = discord.createThread(header, "Match " + match.getId());
      recorder.remember(match, config, thread, header, MatchReportStage.REQUESTED, facts);
      postCard(thread, cardBuilder.build(facts, summary.lines(), playerNames));
      log.info("Stage 1 sent for match {} in thread {}", match.getId(), thread.getIdLong());
      return new PartialReportResult(thread.getIdLong(), header.getIdLong());
    } catch (RuntimeException e) {
      log.warn("Stage 1 of match {} failed: {}", match.getId(), e.getMessage(), e);
      return null;
    }
  }

  /** Stage 2: OpenDota parsed the match; the header says whether a replay analysis follows. */
  public void parsed(MatchDomain match, DbufInstanceConfigDomain config) {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      MatchReportStage stage =
          forecast.isExpected(match) ? MatchReportStage.REPLAY_PENDING : MatchReportStage.PARSED;
      retitle(match.getId(), config.getId(), stage, factsFactory.fromParsed(match, config));
    } catch (RuntimeException e) {
      log.warn("Stage 2 of match {} failed: {}", match.getId(), e.getMessage(), e);
    }
  }

  /** OpenDota never parsed the match; the header keeps what stage 1 said and states that. */
  public void parseTimedOut(long matchId, String instanceId) {
    if (!properties.isEnabled() || instanceId == null) {
      return;
    }
    try {
      recorder
          .find(matchId, instanceId)
          .ifPresentOrElse(
              row -> edit(row, MatchReportStage.PARSE_TIMED_OUT, MatchFacts.fromRow(row)),
              () -> log.debug("No thread of match {} in instance {}", matchId, instanceId));
    } catch (RuntimeException e) {
      log.warn("Timeout title of match {} failed: {}", matchId, e.getMessage(), e);
    }
  }

  private void retitle(long matchId, String instanceId, MatchReportStage stage, MatchFacts facts) {
    recorder
        .find(matchId, instanceId)
        .ifPresentOrElse(
            row -> edit(row, stage, facts),
            () -> log.debug("No thread of match {} in instance {}", matchId, instanceId));
  }

  private void edit(MatchReportThreadDomain row, MatchReportStage stage, MatchFacts facts) {
    if (row.getHeaderMessageId() == null || row.getChannelId() == null) {
      log.debug("Match {} has no header message to retitle", row.getMatchId());
    } else {
      publisher.edit(row.getChannelId(), row.getHeaderMessageId(), headers.render(stage, facts));
    }
    recorder.setStage(row, stage);
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
