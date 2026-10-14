package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Tells the replay side that a freshly played match has a replay to fetch. */
@Component
@RequiredArgsConstructor
public class ReplayRequestAnnouncer {
  private final ApplicationEventPublisher publisher;
  private final ReplayForecast forecast;

  /** Call after the match was persisted. Old and ineligible matches wait for the cron instead. */
  public void announce(MatchDomain match) {
    if (forecast.isRequestable(match)) {
      publisher.publishEvent(new ReplayRequested(match.getId()));
    }
  }
}
