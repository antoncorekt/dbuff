package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Tells the replay side that a stored match has a replay to fetch. */
@Component
@RequiredArgsConstructor
public class ReplayRequestAnnouncer {
  private final ApplicationEventPublisher publisher;

  /** Call after the match was persisted, so a listener finds it in the database. */
  public void announce(MatchDomain match) {
    if (match.getDetailsMappedAt() != null
        && match.getReplayCluster() != null
        && match.getReplaySalt() != null) {
      publisher.publishEvent(new ReplayRequested(match.getId()));
    }
  }
}
