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
   * Stores the thread and header of a match. {@code stage} and {@code facts} are null for a thread
   * of a channel without stages.
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
      row.setThreadId(thread.getIdLong());
      row.setHeaderMessageId(headerMessage.getIdLong());
      row.setCreatedAt(LocalDateTime.now());
      row.setStage(stage);
      if (facts != null) {
        row.setModeName(facts.mode());
        row.setWins(facts.result().wins());
        row.setLosses(facts.result().losses());
        row.setDurationSeconds(facts.durationSeconds());
      }
      threadRepo.save(row);
    } catch (RuntimeException e) {
      log.warn("Could not remember the thread of match {}", match.getId(), e);
    }
  }

  public Optional<MatchReportThreadDomain> find(long matchId, String instanceId) {
    return threadRepo.findByMatchIdAndInstanceId(matchId, instanceId);
  }

  public void setStage(MatchReportThreadDomain row, MatchReportStage stage) {
    try {
      row.setStage(stage);
      threadRepo.save(row);
    } catch (RuntimeException e) {
      log.warn("Could not store stage {} of match {}", stage, row.getMatchId(), e);
    }
  }
}
