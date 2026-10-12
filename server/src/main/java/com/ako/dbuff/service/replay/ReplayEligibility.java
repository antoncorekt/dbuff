package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Decides from the stored match alone whether a replay is worth fetching. */
@Component
@RequiredArgsConstructor
public class ReplayEligibility {
  private static final int SECONDS_PER_MINUTE = 60;

  private final ReplayProcessingProperties properties;
  private final GameModeRegistry modes;

  /** The reason to skip the match, empty when its replay should be fetched. */
  public Optional<ReplayException> skipReason(MatchDomain match) {
    if (match.getReplayCluster() == null || match.getReplaySalt() == null) {
      return skip(ReplayStatus.SKIPPED_NO_REPLAY_INFO, "OpenDota has no replay location");
    }
    if (modes.activeFor(match.getGameModeId()).isEmpty()) {
      return skip(ReplayStatus.SKIPPED_NOT_SUPPORTED, "Game mode " + match.getGameModeId());
    }
    if (match.getDuration() == null || match.getDuration() <= 0) {
      return skip(ReplayStatus.SKIPPED_NOT_PLAYED, "Match has no duration");
    }
    if (isExpired(match)) {
      return skip(ReplayStatus.EXPIRED, "Older than " + properties.getMaxAgeDays() + " days");
    }
    if (match.getDuration() > (long) properties.getMaxDurationMin() * SECONDS_PER_MINUTE) {
      return skip(ReplayStatus.TOO_LARGE, "Match lasted " + match.getDuration() + " s");
    }
    return Optional.empty();
  }

  private boolean isExpired(MatchDomain match) {
    if (match.getStartTime() == null) {
      return false;
    }
    Instant limit = Instant.now().minus(properties.getMaxAgeDays(), ChronoUnit.DAYS);
    return Instant.ofEpochSecond(match.getStartTime()).isBefore(limit);
  }

  private static Optional<ReplayException> skip(ReplayStatus status, String reason) {
    return Optional.of(new ReplayException(status, reason));
  }
}
