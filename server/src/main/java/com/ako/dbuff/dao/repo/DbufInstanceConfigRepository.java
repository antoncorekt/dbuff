package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for DbufInstanceConfigDomain entities. */
@Repository
public interface DbufInstanceConfigRepository
    extends JpaRepository<DbufInstanceConfigDomain, String> {

  /**
   * Find an instance configuration by Discord channel ID.
   *
   * @param discordChannelId the Discord channel ID
   * @return the instance configuration if found
   */
  Optional<DbufInstanceConfigDomain> findByDiscordChannelId(String discordChannelId);

  /**
   * Find all active instance configurations.
   *
   * @return list of active configurations
   */
  List<DbufInstanceConfigDomain> findByActiveTrue();

  /**
   * Find all instance configurations that track a specific player.
   *
   * @param playerId the player ID to search for
   * @return list of configurations tracking this player
   */
  @Query("SELECT d FROM DbufInstanceConfigDomain d JOIN d.players p WHERE p.id = :playerId")
  List<DbufInstanceConfigDomain> findByPlayerId(@Param("playerId") Long playerId);

  /**
   * Find all active instance configurations that track a specific player.
   *
   * @param playerId the player ID to search for
   * @return list of active configurations tracking this player
   */
  @Query(
      "SELECT d FROM DbufInstanceConfigDomain d JOIN d.players p WHERE p.id = :playerId AND d.active = true")
  List<DbufInstanceConfigDomain> findActiveByPlayerId(@Param("playerId") Long playerId);

  /**
   * Check if a Discord channel already has a registered instance.
   *
   * @param discordChannelId the Discord channel ID
   * @return true if an instance exists for this channel
   */
  boolean existsByDiscordChannelId(String discordChannelId);
}
