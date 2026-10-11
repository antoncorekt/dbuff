package com.ako.dbuff.resources.model;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request model for updating an existing Dbuf instance configuration. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateInstanceRequest {

  /** Player IDs to add */
  private Set<Long> addPlayerIds;

  /** Player IDs to remove */
  private Set<Long> removePlayerIds;

  /** Game modes to add */
  private Set<String> addGameModes;

  /** Game modes to remove */
  private Set<String> removeGameModes;

  /** New name for this instance (optional) */
  private String name;

  /** New Discord channel ID (optional) */
  private String discordChannelId;

  /** New Discord guild/server ID (optional) */
  private String discordGuildId;

  /** Whether to activate or deactivate the instance (optional) */
  private Boolean active;

  /** Turn the OpenAI match summary on or off (optional) */
  private Boolean aiSummaryEnabled;

  /** Quiet hours as {@code HH:mm-HH:mm}, {@code off}, or {@code default} (optional) */
  private String quietHours;

  /** IANA timezone id the quiet hours are read in, or {@code default} (optional) */
  private String timezone;

  /** Game modes {@code /stats} defaults to, or just {@code default} to clear (optional) */
  private Set<String> defaultGameModes;

  /** {@code MINIMAL}, {@code STANDARD} or {@code FULL} (optional) */
  private String reportDetail;

  /** Whether any per-channel setting is being changed. */
  public boolean hasSettingsChange() {
    return aiSummaryEnabled != null
        || quietHours != null
        || timezone != null
        || defaultGameModes != null
        || reportDetail != null;
  }
}
