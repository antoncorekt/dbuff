package com.ako.dbuff.service.match;

import com.ako.dbuff.dotapi.model.PlayerRecentMatchesResponse;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
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

  private static PlayerRecentMatchesResponse response(Long slot, Boolean radiantWin) {
    return new PlayerRecentMatchesResponse()
        .matchId(5L)
        .startTime(1000L)
        .duration(2100L)
        .gameMode(22L)
        .playerSlot(slot)
        .radiantWin(radiantWin)
        .heroId(14L)
        .kills(10L)
        .deaths(2L)
        .assists(5L)
        .goldPerMin(512L)
        .xpPerMin(640L);
  }

  @Test
  void toRecentMatch_carriesTheTrackedPlayersLineWithoutAnotherRequest() {
    RecentMatch recent = DotaApiLastMatchesService.toRecentMatch(42L, response(130L, false));

    assertThat(recent.id()).isEqualTo(5L);
    assertThat(recent.duration()).isEqualTo(2100L);
    assertThat(recent.gameMode()).isEqualTo(22L);
    assertThat(recent.line())
        .isEqualTo(new PlayerLine(42L, 130L, 14L, 10L, 2L, 5L, 512L, 640L, false));
    assertThat(recent.line().won()).isTrue();
  }

  @Test
  void won_comparesTheWinningSideWithTheSideOfTheSlot() {
    assertThat(line(0L, true).won()).isTrue();
    assertThat(line(4L, false).won()).isFalse();
    assertThat(line(128L, false).won()).isTrue();
    assertThat(line(132L, true).won()).isFalse();
  }

  @Test
  void won_isUnknownWhenOpenDotaOmittedTheSlotOrTheWinner() {
    assertThat(line(null, true).won()).isNull();
    assertThat(line(0L, null).won()).isNull();
    assertThat(
            DotaApiLastMatchesService.toRecentMatch(
                    1L, new PlayerRecentMatchesResponse().matchId(3L))
                .line()
                .won())
        .isNull();
  }

  private static PlayerLine line(Long slot, Boolean radiantWin) {
    return new PlayerLine(1L, slot, null, null, null, null, null, null, radiantWin);
  }
}
