package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.mode.GameModeRegistry;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayEligibilityTest {

  private final ReplayProcessingProperties properties = new ReplayProcessingProperties();
  private final ReplayEligibility eligibility = new ReplayEligibility(properties);
  private final GameModeRegistry modes = TestModes.registry(properties);

  @Test
  void skipReason_isEmptyForARecentSupportedMatchWithAReplayLocation() {
    assertThat(status(match())).isEmpty();
  }

  @Test
  void skipReason_namesTheFirstReasonTheReplayIsNotWorthFetching() {
    MatchDomain noLocation = match();
    noLocation.setReplaySalt(null);
    MatchDomain wrongMode = match();
    wrongMode.setGameModeId(3L);
    MatchDomain neverStarted = match();
    neverStarted.setDuration(0L);
    MatchDomain old = match();
    old.setStartTime(Instant.now().minus(120, ChronoUnit.DAYS).getEpochSecond());
    MatchDomain huge = match();
    huge.setDuration(3 * 3600L);

    assertThat(status(noLocation)).contains(ReplayStatus.SKIPPED_NO_REPLAY_INFO);
    assertThat(status(wrongMode)).contains(ReplayStatus.SKIPPED_NOT_SUPPORTED);
    assertThat(status(neverStarted)).contains(ReplayStatus.SKIPPED_NOT_PLAYED);
    assertThat(status(old)).contains(ReplayStatus.EXPIRED);
    assertThat(status(huge)).contains(ReplayStatus.TOO_LARGE);
  }

  @Test
  void skipReason_followsTheConfiguredModesThatHaveAProfile() {
    MatchDomain allPick = match();
    allPick.setGameModeId(22L);
    properties.setSupportedModes(java.util.List.of(18L, 23L));
    MatchDomain unregistered = match();
    unregistered.setGameModeId(23L);

    assertThat(status(allPick)).contains(ReplayStatus.SKIPPED_NOT_SUPPORTED);
    assertThat(status(unregistered)).contains(ReplayStatus.SKIPPED_NOT_SUPPORTED);
    assertThat(status(match())).isEmpty();
  }

  private Optional<ReplayStatus> status(MatchDomain match) {
    return eligibility
        .skipReason(match, modes.activeFor(match.getGameModeId()))
        .map(ReplayException::getStatus);
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    match.setReplayCluster(1L);
    match.setReplaySalt(2L);
    match.setGameModeId(18L);
    match.setDuration(2700L);
    match.setStartTime(Instant.now().minus(2, ChronoUnit.DAYS).getEpochSecond());
    return match;
  }
}
