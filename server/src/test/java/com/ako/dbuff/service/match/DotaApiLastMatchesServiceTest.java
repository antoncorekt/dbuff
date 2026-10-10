package com.ako.dbuff.service.match;

import com.ako.dbuff.service.match.DotaApiLastMatchesService.RecentMatch;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DotaApiLastMatchesServiceTest {

  private static long epoch(LocalDateTime time) {
    return time.atZone(ZoneId.systemDefault()).toEpochSecond();
  }

  @Test
  void endedBefore_usesStartPlusDuration() {
    LocalDateTime moment = LocalDateTime.of(2026, 5, 1, 12, 0);
    long start = epoch(moment.minusMinutes(30));

    assertThat(new RecentMatch(1, start, 1500L, 22L).endedBefore(moment)).isTrue();
    // Started before the moment but finished after it: that is live news, not history.
    assertThat(new RecentMatch(1, start, 2400L, 22L).endedBefore(moment)).isFalse();
  }

  @Test
  void endedBefore_unknownStart_isNeverHistory() {
    assertThat(new RecentMatch(1, null, null, null).endedBefore(LocalDateTime.now())).isFalse();
  }

  @Test
  void endedBefore_unknownDuration_fallsBackToTheStart() {
    LocalDateTime moment = LocalDateTime.of(2026, 5, 1, 12, 0);

    assertThat(new RecentMatch(1, epoch(moment.minusHours(1)), null, null).endedBefore(moment))
        .isTrue();
  }
}
