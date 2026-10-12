package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Tells the replay side that a freshly played match has a replay to fetch. */
@Component
@RequiredArgsConstructor
public class ReplayRequestAnnouncer {
  private final ApplicationEventPublisher publisher;
  private final ReplayEligibility eligibility;
  private final GameModeRegistry modes;
  private final ReplayProcessingProperties properties;

  /** Call after the match was persisted. Old and ineligible matches wait for the cron instead. */
  public void announce(MatchDomain match) {
    if (match.getDetailsMappedAt() != null
        && startedRecently(match)
        && eligibility.skipReason(match, modes.activeFor(match.getGameModeId())).isEmpty()) {
      publisher.publishEvent(new ReplayRequested(match.getId()));
    }
  }

  private boolean startedRecently(MatchDomain match) {
    Instant limit = Instant.now().minus(properties.getFreshWindowHours(), ChronoUnit.HOURS);
    return match.getStartTime() != null
        && Instant.ofEpochSecond(match.getStartTime()).isAfter(limit);
  }
}
