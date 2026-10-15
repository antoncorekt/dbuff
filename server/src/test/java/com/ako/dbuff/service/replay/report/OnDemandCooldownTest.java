package com.ako.dbuff.service.replay.report;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnDemandCooldownTest {
  private static final class MutableClock extends Clock {
    private long millis = 1_000_000L;

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }
  }

  private final MutableClock clock = new MutableClock();
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final OnDemandCooldown cooldown = new OnDemandCooldown(properties, clock);

  @Test
  void defaultIsThirtySeconds() {
    assertThat(properties.getOnDemandCooldownSeconds()).isEqualTo(30);
  }

  @Test
  void secondRequestInTheSameThreadWaitsAndAnotherThreadDoesNot() {
    assertThat(cooldown.acquire("t1")).isZero();
    clock.millis += 10_500;

    assertThat(cooldown.acquire("t1")).isEqualTo(20);
    assertThat(cooldown.acquire("t2")).isZero();
  }

  @Test
  void afterTheWaitTheThreadMayAskAgain() {
    cooldown.acquire("t1");
    clock.millis += 30_000;

    assertThat(cooldown.acquire("t1")).isZero();
  }

  @Test
  void aRefusedRequestDoesNotExtendTheWait() {
    cooldown.acquire("t1");
    clock.millis += 20_000;
    assertThat(cooldown.acquire("t1")).isEqualTo(10);
    clock.millis += 10_000;

    assertThat(cooldown.acquire("t1")).isZero();
  }

  @Test
  void zeroTurnsItOff() {
    properties.setOnDemandCooldownSeconds(0);

    assertThat(cooldown.acquire("t1")).isZero();
    assertThat(cooldown.acquire("t1")).isZero();
  }

  @Test
  void release_letsTheThreadAskAgainAtOnce() {
    cooldown.acquire("t1");
    cooldown.release("t1");

    assertThat(cooldown.acquire("t1")).isZero();
  }
}
