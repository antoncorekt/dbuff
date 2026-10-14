package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a freshly mapped match gets its replay fetched at once. The announcer acts on it and the
 * match header promises it, so the two cannot disagree.
 */
@Component
@RequiredArgsConstructor
public class ReplayForecast {
  private final ReplayEligibility eligibility;
  private final GameModeRegistry modes;
  private final ReplayProcessingProperties properties;

  /** The match has mapped details, started within the fresh window and is eligible. */
  public boolean isRequestable(MatchDomain match) {
    return match.getDetailsMappedAt() != null
        && startedRecently(match)
        && eligibility.skipReason(match, modes.activeFor(match.getGameModeId())).isEmpty();
  }

  /** Replay processing is on and the match is requestable. */
  public boolean isExpected(MatchDomain match) {
    return properties.isEnabled() && isRequestable(match);
  }

  private boolean startedRecently(MatchDomain match) {
    Instant limit = Instant.now().minus(properties.getFreshWindowHours(), ChronoUnit.HOURS);
    return match.getStartTime() != null
        && Instant.ofEpochSecond(match.getStartTime()).isAfter(limit);
  }
}
