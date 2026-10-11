package com.ako.dbuff.dao.model;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceSettingsTest {

  @Test
  void anUntouchedChannel_behavesAsBefore() {
    InstanceSettings settings = new InstanceSettings();
    assertThat(settings.isReplayReportOn()).as("unset means on").isTrue();

    assertThat(settings.isAiSummaryOn()).isTrue();
    assertThat(settings.detail()).isEqualTo(ReportDetail.FULL);
    assertThat(settings.zone()).isEqualTo(ZoneId.systemDefault());
    assertThat(settings.defaultGameModeTokens()).isEmpty();
    assertThat(settings.getQuietHoursEnabled()).isNull();
  }

  @Test
  void aStoredZoneThatNoLongerParses_fallsBackToTheServerZone() {
    InstanceSettings settings = InstanceSettings.builder().timezone("Not/AZone").build();

    assertThat(settings.zone()).isEqualTo(ZoneId.systemDefault());
  }

  @Test
  void blankDefaultModes_areNone() {
    assertThat(InstanceSettings.builder().defaultGameModes(" ").build().defaultGameModeTokens())
        .isEmpty();
    assertThat(
            InstanceSettings.builder()
                .defaultGameModes("game_mode_a, game_mode_b,,")
                .build()
                .defaultGameModeTokens())
        .containsExactly("game_mode_a", "game_mode_b");
  }

  @Test
  void detailLevels_nestFromMinimalToFull() {
    assertThat(ReportDetail.FULL.includes(ReportDetail.STANDARD)).isTrue();
    assertThat(ReportDetail.STANDARD.includes(ReportDetail.STANDARD)).isTrue();
    assertThat(ReportDetail.STANDARD.includes(ReportDetail.FULL)).isFalse();
    assertThat(ReportDetail.MINIMAL.includes(ReportDetail.MINIMAL)).isTrue();
    assertThat(ReportDetail.MINIMAL.includes(ReportDetail.STANDARD)).isFalse();
  }

  @Test
  void aRowWithNoSettings_yieldsDefaultsNotNull() {
    DbufInstanceConfigDomain domain = new DbufInstanceConfigDomain();

    assertThat(domain.getSettings()).isNull();
    assertThat(domain.effectiveSettings()).isNotNull();
    assertThat(domain.effectiveSettings().isAiSummaryOn()).isTrue();
  }
}
