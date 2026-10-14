package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReplayRequestAnnouncerTest {

  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayRequestAnnouncer announcer =
      new ReplayRequestAnnouncer(
          publisher,
          new ReplayForecast(
              new ReplayEligibility(properties), TestModes.registry(properties), properties));

  @Test
  void announce_publishesForAFreshEligibleMatch() {
    announcer.announce(mapped());

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

  @Test
  void announce_staysSilentForAMatchThatStartedLongAgo() {
    MatchDomain match = mapped();
    match.setStartTime(Instant.now().minusSeconds(7 * 3600).getEpochSecond());

    announcer.announce(match);

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void announce_staysSilentForAnUnsupportedModeOrNoDuration() {
    MatchDomain otherMode = mapped();
    otherMode.setGameModeId(3L);
    MatchDomain notPlayed = mapped();
    notPlayed.setDuration(0L);

    announcer.announce(otherMode);
    announcer.announce(notPlayed);

    verify(publisher, never()).publishEvent(any(Object.class));
  }

  private static MatchDomain mapped() {
    MatchDomain match = new MatchDomain();
    match.setId(7L);
    match.setDetailsMappedAt(LocalDateTime.now());
    match.setStartTime(Instant.now().minusSeconds(3600).getEpochSecond());
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    match.setGameModeId(18L);
    match.setDuration(2400L);
    return match;
  }
}
