package com.ako.dbuff.resources.model;

import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request model for registering a new Dbuf instance configuration. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterInstanceRequest {

  /** Player IDs to track (required) */
  private Set<Long> playerIds;

  /** Game modes to track (optional, defaults to all modes if not specified) */
  private Set<String> gameModes;

  /** Discord channel ID (optional, for Discord bot registration) */
  private String discordChannelId;

  /** Discord guild/server ID (optional) */
  private String discordGuildId;

  /** Name for this instance configuration (optional) */
  private String name;

  /** Additional configuration for future extensibility */
  private Map<String, Object> extraConfig;
}
