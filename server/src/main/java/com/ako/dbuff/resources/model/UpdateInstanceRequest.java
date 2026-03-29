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
}
