package com.ako.dbuff.dao.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain entity representing a Dbuf instance configuration. Each instance is registered by a user
 * (typically via Discord) and contains the configuration for which players and game modes to track.
 */
@Entity
@Table(name = "dbuf_instance_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DbufInstanceConfigDomain {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /** Discord channel ID where this instance is registered (optional) */
  @Column(name = "discord_channel_id")
  private String discordChannelId;

  /** Discord guild/server ID (optional) */
  @Column(name = "discord_guild_id")
  private String discordGuildId;

  /** Name for this instance configuration */
  @Column(name = "name")
  private String name;

  /**
   * Players to track for this instance. Uses ManyToMany relationship with existing PlayerDomain
   * entity.
   */
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "dbuf_instance_config_players",
      joinColumns = @JoinColumn(name = "instance_id"),
      inverseJoinColumns = @JoinColumn(name = "player_name"))
  @Builder.Default
  private Set<PlayerDomain> players = new HashSet<>();

  /**
   * Game mode IDs to track (numeric IDs matching MatchDomain.gameModeId). These IDs are validated
   * against constantsManagers.getMatchTypeConstantMap().
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "dbuf_instance_game_modes",
      joinColumns = @JoinColumn(name = "instance_id"))
  @Column(name = "game_mode_id")
  @Builder.Default
  private Set<Long> gameModeIds = new HashSet<>();

  /** Additional configuration stored as JSON for future extensibility */
  @Column(name = "extra_config", columnDefinition = "TEXT")
  private String extraConfig;

  /** When this instance was created */
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  /** When this instance was last updated */
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /**
   * When tracking began (or last widened to new players). Matches that ended before it are imported
   * quietly for statistics instead of being reported. Null on instances that predate the field,
   * which keeps reporting everything, as they always did.
   */
  @Column(name = "tracking_since")
  private LocalDateTime trackingSince;

  /**
   * Per-player cutoffs for players added after registration, keyed by account ID. A player added
   * later brings recent matches that are history, but the players already tracked must keep
   * reporting theirs, so the instance-wide {@link #trackingSince} cannot be moved for them.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "dbuf_instance_player_tracking_since",
      joinColumns = @JoinColumn(name = "instance_id"))
  @MapKeyColumn(name = "player_id")
  @Column(name = "tracking_since")
  @Builder.Default
  private Map<Long, LocalDateTime> playerTrackingSince = new HashMap<>();

  /**
   * Per-channel choices; null on rows that never set any. Read via {@link #effectiveSettings()}.
   */
  @Embedded private InstanceSettings settings;

  /** Whether this instance is active */
  @Column(name = "active")
  @Builder.Default
  private Boolean active = true;

  /** The settings with null-for-unset resolved, never null. */
  public InstanceSettings effectiveSettings() {
    return settings == null ? new InstanceSettings() : settings;
  }

  /**
   * The moment before which this player's matches are history.
   *
   * @return the player's own cutoff, else the instance's; null when neither exists, meaning
   *     everything is reported
   */
  public LocalDateTime trackingSinceFor(Long playerId) {
    LocalDateTime own = playerTrackingSince == null ? null : playerTrackingSince.get(playerId);
    return own != null ? own : trackingSince;
  }

  /**
   * Helper method to get player IDs from the players set.
   *
   * @return Set of player IDs
   */
  public Set<Long> getPlayerIds() {
    Set<Long> ids = new HashSet<>();
    if (players != null) {
      for (PlayerDomain player : players) {
        if (player.getId() != null) {
          ids.add(player.getId());
        }
      }
    }
    return ids;
  }
}
