package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-channel choices, each nullable so that a column never written means "behave as before".
 * Hibernate loads an embedded object whose columns are all null as null itself, so readers go
 * through {@link DbufInstanceConfigDomain#effectiveSettings()} rather than the field.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceSettings {

  /** Null means on. */
  @Column(name = "ai_summary_enabled")
  private Boolean aiSummaryEnabled;

  /**
   * Null follows the server-wide quiet hours, false turns them off for this channel, true uses
   * {@link #quietStart} and {@link #quietEnd}.
   */
  @Column(name = "quiet_hours_enabled")
  private Boolean quietHoursEnabled;

  @Column(name = "quiet_start")
  private LocalTime quietStart;

  @Column(name = "quiet_end")
  private LocalTime quietEnd;

  /** IANA zone id the quiet window is read in; null means the server's zone. */
  @Column(name = "timezone")
  private String timezone;

  /** Comma-separated game mode tokens {@code /stats} uses when none is given; null means none. */
  @Column(name = "default_game_modes")
  private String defaultGameModes;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_detail")
  private ReportDetail reportDetail;

  public boolean isAiSummaryOn() {
    return aiSummaryEnabled == null || aiSummaryEnabled;
  }

  public ReportDetail detail() {
    return reportDetail == null ? ReportDetail.FULL : reportDetail;
  }

  /** The zone quiet hours are read in; an unreadable stored id falls back to the server's. */
  public ZoneId zone() {
    if (timezone == null || timezone.isBlank()) {
      return ZoneId.systemDefault();
    }
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException e) {
      return ZoneId.systemDefault();
    }
  }

  /** Tokens for the resolver; empty when the channel has no default of its own. */
  public Set<String> defaultGameModeTokens() {
    if (defaultGameModes == null || defaultGameModes.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(defaultGameModes.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }
}
