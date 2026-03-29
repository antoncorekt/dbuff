package com.ako.dbuff.resources.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model representing a Dbuf instance configuration. This is the API representation of
 * DbufInstanceConfigDomain.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DbufInstanceConfigResponse {

  /** Unique identifier for this instance */
  private String id;

  /** Discord channel ID where this instance is registered (optional) */
  private String discordChannelId;

  /** Discord guild/server ID (optional) */
  private String discordGuildId;

  /** Name for this instance configuration */
  private String name;

  /** Players being tracked (with ID and name) */
  private Set<PlayerInfo> players;

  /** Game modes being tracked */
  private Set<String> gameModes;

  /** Additional configuration */
  private Map<String, Object> extraConfig;

  /** When this instance was created */
  private LocalDateTime createdAt;

  /** When this instance was last updated */
  private LocalDateTime updatedAt;

  /** Whether this instance is active */
  private Boolean active;
}
