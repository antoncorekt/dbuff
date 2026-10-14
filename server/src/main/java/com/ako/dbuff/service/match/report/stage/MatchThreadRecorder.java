package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Component;

/** Keeps the thread of a match, so that a report arriving hours later can find it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchThreadRecorder {
  private final MatchReportThreadRepo threadRepo;

  /**
   * Stores the thread and header of a match. {@code stage} is null for a header that is plain text,
   * which is only ever edited as text; {@code thread} is null when it could not be created.
   */
  public void remember(
      MatchDomain match,
      DbufInstanceConfigDomain config,
      ThreadChannel thread,
      Message headerMessage,
      MatchReportStage stage,
      MatchFacts facts) {
    try {
      MatchReportThreadDomain row =
          threadRepo
              .findByMatchIdAndInstanceId(match.getId(), config.getId())
              .orElseGet(MatchReportThreadDomain::new);
      row.setMatchId(match.getId());
      row.setInstanceId(config.getId());
      row.setChannelId(config.getDiscordChannelId());
      row.setThreadId(thread == null ? null : thread.getIdLong());
      row.setHeaderMessageId(headerMessage.getIdLong());
      row.setCreatedAt(LocalDateTime.now());
      row.setStage(stage);
      row.setModeName(facts == null ? null : facts.mode());
      row.setWins(facts == null ? null : facts.result().wins());
      row.setLosses(facts == null ? null : facts.result().losses());
      row.setDurationSeconds(facts == null ? null : facts.durationSeconds());
      threadRepo.save(row);
    } catch (RuntimeException e) {
      log.warn("Could not remember the thread of match {}", match.getId(), e);
    }
  }

  public Optional<MatchReportThreadDomain> find(long matchId, String instanceId) {
    return threadRepo.findByMatchIdAndInstanceId(matchId, instanceId);
  }

  /**
   * Moves the row to a later stage and keeps the facts the new title was made of, in one update of
   * those columns only; false when the row was no longer in a stage this one may follow.
   */
  public boolean advance(MatchReportThreadDomain row, MatchReportStage stage, MatchFacts facts) {
    if (stage.predecessors().isEmpty()) {
      return false;
    }
    try {
      boolean known = facts.result().known();
      return threadRepo.advanceStage(
              row.getId(),
              stage,
              stage.predecessors(),
              facts.mode(),
              known ? facts.result().wins() : null,
              known ? facts.result().losses() : null,
              facts.durationSeconds())
          > 0;
    } catch (RuntimeException e) {
      log.warn("Could not store stage {} of match {}", stage, row.getMatchId(), e);
      return false;
    }
  }
}
