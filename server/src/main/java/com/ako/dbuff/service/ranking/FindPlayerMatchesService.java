package com.ako.dbuff.service.ranking;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.FindPlayerMatchesRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for finding player matches with statistics. Provides functionality to search for a player
 * by name and retrieve their matches with statistics for tracked players.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FindPlayerMatchesService {

  private static final int DEFAULT_LIMIT = 20;

  private final PlayerRepo playerRepo;
  private final FindPlayerMatchesRepository findPlayerMatchesRepository;
  private final DbufInstanceConfigService instanceConfigService;

  /**
   * Finds matches for a player by their name using instance context.
   *
   * @param playerName The player's name to search for
   * @param limit Maximum number of matches to return. Defaults to 20 if null.
   * @return List of FindPlayerMatchesResponse ordered by match date descending. Returns empty list
   *     if player not found.
   */
  @Transactional(readOnly = true)
  public List<FindPlayerMatchesResponse> findPlayerMatches(String playerName, Integer limit) {
    Set<Long> trackedPlayerIds = getContextPlayerIds();
    return findPlayerMatches(playerName, limit, trackedPlayerIds);
  }

  /**
   * Finds matches for a player by their name for a specific instance.
   *
   * @param instanceId The instance configuration ID
   * @param playerName The player's name to search for
   * @param limit Maximum number of matches to return. Defaults to 20 if null.
   * @return List of FindPlayerMatchesResponse ordered by match date descending. Returns empty list
   *     if player not found or instance not found.
   */
  @Transactional(readOnly = true)
  public List<FindPlayerMatchesResponse> findPlayerMatchesForInstance(
      String instanceId, String playerName, Integer limit) {

    Optional<DbufInstanceConfigDomain> instanceOpt =
        instanceConfigService.getDomainById(instanceId);
    if (instanceOpt.isEmpty()) {
      log.warn("Instance not found: {}", instanceId);
      return List.of();
    }

    Set<Long> trackedPlayerIds = instanceOpt.get().getPlayerIds();
    return findPlayerMatches(playerName, limit, trackedPlayerIds);
  }

  /**
   * Finds matches for a player by their name with specified tracked player IDs.
   *
   * @param playerName The player's name to search for
   * @param limit Maximum number of matches to return. Defaults to 20 if null.
   * @param trackedPlayerIds The player IDs to include in statistics
   * @return List of FindPlayerMatchesResponse ordered by match date descending. Returns empty list
   *     if player not found.
   */
  @Transactional(readOnly = true)
  public List<FindPlayerMatchesResponse> findPlayerMatches(
      String playerName, Integer limit, Set<Long> trackedPlayerIds) {

    log.info(
        "Finding matches for player '{}' with limit={}, trackedPlayers={}",
        playerName,
        limit,
        trackedPlayerIds.size());

    // Find player by name
    Optional<PlayerDomain> playerOpt = playerRepo.findByName(playerName);

    if (playerOpt.isEmpty()) {
      log.info("Player '{}' not found", playerName);
      return List.of();
    }

    PlayerDomain player = playerOpt.get();
    Long playerId = player.getId();

    log.info("Found player '{}' with ID {}", playerName, playerId);

    // Use default limit if not specified
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    // Find matches with statistics
    List<FindPlayerMatchesResponse> matches =
        findPlayerMatchesRepository.findPlayerMatches(
            playerId, playerName, trackedPlayerIds, effectiveLimit);

    log.info("Found {} matches for player '{}'", matches.size(), playerName);

    return matches;
  }

  /**
   * Gets player IDs from the current instance context.
   *
   * @return set of player IDs from context, or empty set if no context
   */
  private Set<Long> getContextPlayerIds() {
    String instanceId = ProcessContext.getCurrentInstanceId();
    if (instanceId != null) {
      return instanceConfigService
          .getDomainById(instanceId)
          .map(DbufInstanceConfigDomain::getPlayerIds)
          .orElse(Set.of());
    }
    return Set.of();
  }
}
