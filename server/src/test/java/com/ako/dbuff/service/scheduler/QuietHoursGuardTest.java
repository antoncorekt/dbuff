package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.config.QuietHoursProperties;
import com.ako.dbuff.dao.model.InstanceSettings;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuietHoursGuardTest {

  private QuietHoursGuard guard(boolean enabled, LocalTime start, LocalTime end) {
    QuietHoursProperties props = new QuietHoursProperties();
    props.setEnabled(enabled);
    props.setStart(start);
    props.setEnd(end);
    return new QuietHoursGuard(props);
  }

  @Test
  void withinWindow_isQuiet() {
    QuietHoursGuard guard = guard(true, LocalTime.of(1, 0), LocalTime.of(9, 0));

    assertThat(guard.isQuietTime(LocalTime.of(1, 0))).isTrue(); // start inclusive
    assertThat(guard.isQuietTime(LocalTime.of(5, 30))).isTrue();
    assertThat(guard.isQuietTime(LocalTime.of(8, 59))).isTrue();
  }

  @Test
  void outsideWindow_isNotQuiet() {
    QuietHoursGuard guard = guard(true, LocalTime.of(1, 0), LocalTime.of(9, 0));

    assertThat(guard.isQuietTime(LocalTime.of(9, 0))).isFalse(); // end exclusive
    assertThat(guard.isQuietTime(LocalTime.of(0, 59))).isFalse();
    assertThat(guard.isQuietTime(LocalTime.of(15, 0))).isFalse();
  }

  @Test
  void serverWideCheck_isNeverQuietWhenDisabled() {
    assertThat(guard(false, LocalTime.MIN, LocalTime.MAX).isServerQuietTime()).isFalse();
  }

  @Test
  void disabled_isNeverQuiet() {
    QuietHoursGuard guard = guard(false, LocalTime.of(1, 0), LocalTime.of(9, 0));

    assertThat(guard.isQuietTime(LocalTime.of(5, 0))).isFalse();
  }

  @Test
  void windowWrappingMidnight_isHandled() {
    QuietHoursGuard guard = guard(true, LocalTime.of(23, 0), LocalTime.of(7, 0));

    assertThat(guard.isQuietTime(LocalTime.of(23, 30))).isTrue();
    assertThat(guard.isQuietTime(LocalTime.of(3, 0))).isTrue();
    assertThat(guard.isQuietTime(LocalTime.of(12, 0))).isFalse();
  }

  // ------------------------------------------------------------- per-channel settings

  private static final java.time.Instant NIGHT_UTC =
      java.time.Instant.parse("2026-05-01T23:00:00Z");

  @Test
  void channelWithNoChoice_followsTheServerWindow() {
    QuietHoursGuard guard = guard(true, LocalTime.of(1, 0), LocalTime.of(9, 0));
    InstanceSettings settings = InstanceSettings.builder().timezone("UTC").build();

    assertThat(guard.isQuietTime(settings, java.time.Instant.parse("2026-05-01T05:00:00Z")))
        .isTrue();
    assertThat(guard.isQuietTime(settings, NIGHT_UTC)).isFalse();
  }

  @Test
  void channelWithNoChoice_andServerWindowOff_isNeverQuiet() {
    QuietHoursGuard guard = guard(false, LocalTime.of(1, 0), LocalTime.of(9, 0));

    assertThat(
            guard.isQuietTime(
                new InstanceSettings(), java.time.Instant.parse("2026-05-01T05:00:00Z")))
        .isFalse();
  }

  @Test
  void channelThatTurnedQuietHoursOff_isNeverQuiet_whateverTheServerSays() {
    QuietHoursGuard guard = guard(true, LocalTime.of(0, 0), LocalTime.of(23, 59));
    InstanceSettings settings =
        InstanceSettings.builder().quietHoursEnabled(false).timezone("UTC").build();

    assertThat(guard.isQuietTime(settings, java.time.Instant.parse("2026-05-01T05:00:00Z")))
        .isFalse();
  }

  @Test
  void channelWindow_isReadInTheChannelsTimezone() {
    QuietHoursGuard guard = guard(false, LocalTime.of(1, 0), LocalTime.of(9, 0));
    InstanceSettings tokyo =
        InstanceSettings.builder()
            .quietHoursEnabled(true)
            .quietStart(LocalTime.of(1, 0))
            .quietEnd(LocalTime.of(9, 0))
            .timezone("Asia/Tokyo")
            .build();
    InstanceSettings utc =
        InstanceSettings.builder()
            .quietHoursEnabled(true)
            .quietStart(LocalTime.of(1, 0))
            .quietEnd(LocalTime.of(9, 0))
            .timezone("UTC")
            .build();

    // 23:00 UTC is 08:00 in Tokyo, inside the window there and outside it in UTC.
    assertThat(guard.isQuietTime(tokyo, NIGHT_UTC)).isTrue();
    assertThat(guard.isQuietTime(utc, NIGHT_UTC)).isFalse();
  }

  @Test
  void channelWindow_mayWrapMidnight() {
    QuietHoursGuard guard = guard(false, LocalTime.of(1, 0), LocalTime.of(9, 0));
    InstanceSettings settings =
        InstanceSettings.builder()
            .quietHoursEnabled(true)
            .quietStart(LocalTime.of(22, 0))
            .quietEnd(LocalTime.of(7, 0))
            .timezone("UTC")
            .build();

    assertThat(guard.isQuietTime(settings, NIGHT_UTC)).isTrue();
    assertThat(guard.isQuietTime(settings, java.time.Instant.parse("2026-05-01T12:00:00Z")))
        .isFalse();
  }
}
