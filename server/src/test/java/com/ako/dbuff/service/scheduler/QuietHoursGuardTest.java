package com.ako.dbuff.service.scheduler;

import com.ako.dbuff.config.QuietHoursProperties;
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
}
