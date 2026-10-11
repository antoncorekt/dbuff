package com.ako.dbuff.resources.model;

import com.ako.dbuff.dao.model.InstanceSettings;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The settings that apply to a channel, unset values already resolved to their defaults. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InstanceSettingsResponse {

  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

  private boolean aiSummaryEnabled;

  /** {@code server default}, {@code off}, or {@code HH:mm-HH:mm}. */
  private String quietHours;

  /** An IANA zone id, or {@code server default}. */
  private String timezone;

  /**
   * Mode tokens as stored, ready for the resolver; empty means {@code /stats} uses Ability Draft.
   */
  private Set<String> defaultGameModes;

  private String reportDetail;

  public static InstanceSettingsResponse from(InstanceSettings settings) {
    Boolean quiet = settings.getQuietHoursEnabled();
    return InstanceSettingsResponse.builder()
        .aiSummaryEnabled(settings.isAiSummaryOn())
        .quietHours(
            quiet == null
                ? "server default"
                : quiet
                    ? clock(settings.getQuietStart()) + "-" + clock(settings.getQuietEnd())
                    : "off")
        .timezone(
            settings.getTimezone() == null || settings.getTimezone().isBlank()
                ? "server default"
                : settings.getTimezone())
        .defaultGameModes(settings.defaultGameModeTokens())
        .reportDetail(settings.detail().name())
        .build();
  }

  private static String clock(LocalTime time) {
    return time == null ? "?" : time.format(CLOCK);
  }
}
