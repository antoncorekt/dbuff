package com.ako.dbuff.service.replay.clarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameClockTest {

  @Test
  void gameTick_standsStillWhilePausedAndKeepsTheLossAfterwards() {
    GameClock clock = new GameClock();

    assertThat(clock.gameTick(1000)).isEqualTo(1000);

    clock.paused(true, 2000);
    assertThat(clock.gameTick(2000)).isEqualTo(2000);
    assertThat(clock.gameTick(2500)).isEqualTo(2000);

    clock.paused(false, 3000);
    assertThat(clock.gameTick(3000)).isEqualTo(2000);
    assertThat(clock.gameTick(3100)).isEqualTo(2100);

    clock.paused(true, 4000);
    clock.paused(false, 4500);
    assertThat(clock.gameTick(5000)).isEqualTo(5000 - 1000 - 500);
  }

  @Test
  void paused_ignoresRepeatedFlags() {
    GameClock clock = new GameClock();
    clock.paused(false, 100);
    clock.paused(true, 200);
    clock.paused(true, 300);
    clock.paused(false, 400);
    clock.paused(false, 500);

    assertThat(clock.gameTick(600)).isEqualTo(400);
  }
}
