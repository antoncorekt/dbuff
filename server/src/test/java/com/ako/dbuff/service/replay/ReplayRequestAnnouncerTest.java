package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReplayRequestAnnouncerTest {

  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final ReplayRequestAnnouncer announcer = new ReplayRequestAnnouncer(publisher);

  @Test
  void announce_publishesWhenDetailsAreMappedAndTheReplayLocationIsKnown() {
    MatchDomain match = mapped();

    announcer.announce(match);

    verify(publisher).publishEvent(new ReplayRequested(7L));
  }

  @Test
  void announce_staysSilentWithoutMappedDetails() {
    MatchDomain match = mapped();
    match.setDetailsMappedAt(null);

    announcer.announce(match);

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void announce_staysSilentWithoutAReplayLocation() {
    MatchDomain match = mapped();
    match.setReplaySalt(null);

    announcer.announce(match);

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  private static MatchDomain mapped() {
    MatchDomain match = new MatchDomain();
    match.setId(7L);
    match.setDetailsMappedAt(LocalDateTime.now());
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    return match;
  }
}
