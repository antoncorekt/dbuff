package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReplayForecastTest {

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

  private static Arguments variant(String name, Consumer<MatchDomain> change) {
    return Arguments.of(name, change);
  }

  static Stream<Arguments> matches() {
    return Stream.of(
        variant("fresh and eligible", m -> {}),
        variant("details not mapped", m -> m.setDetailsMappedAt(null)),
        variant("no replay location", m -> m.setReplaySalt(null)),
        variant(
            "started long ago",
            m -> m.setStartTime(Instant.now().minusSeconds(7 * 3600).getEpochSecond())),
        variant("no start time", m -> m.setStartTime(null)),
        variant("mode not active", m -> m.setGameModeId(3L)),
        variant("not played", m -> m.setDuration(0L)),
        variant("too long", m -> m.setDuration(130L * 60)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("matches")
  void theHeaderForecastIsWhatTheAnnouncerActsOn(String name, Consumer<MatchDomain> change) {
    ReplayProcessingProperties properties = new ReplayProcessingProperties();
    properties.setEnabled(true);
    ReplayForecast forecast =
        new ReplayForecast(
            new ReplayEligibility(properties), TestModes.registry(properties), properties);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    MatchDomain match = mapped();
    change.accept(match);

    new ReplayRequestAnnouncer(publisher, forecast).announce(match);

    if (forecast.isExpected(match)) {
      verify(publisher).publishEvent(new ReplayRequested(7L));
    } else {
      verify(publisher, never()).publishEvent(any(Object.class));
    }
    assertThat(forecast.isExpected(match)).isEqualTo(forecast.isRequestable(match));
  }

  @Test
  void nothingIsExpectedWhileReplayProcessingIsOff() {
    ReplayProcessingProperties properties = new ReplayProcessingProperties();
    ReplayForecast forecast =
        new ReplayForecast(
            new ReplayEligibility(properties), TestModes.registry(properties), properties);

    assertThat(forecast.isRequestable(mapped())).isTrue();
    assertThat(forecast.isExpected(mapped())).isFalse();
  }
}
