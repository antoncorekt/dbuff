package com.ako.dbuff.service.ranking;

import com.ako.dbuff.config.PlayerConfiguration;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.FindPlayerMatchesRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.FindPlayerMatchesResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for finding player matches with statistics. Provides functionality to search for a player
 * by name and retrieve their matches with statistics for default players.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FindPlayerMatchesService {

  private static final int DEFAULT_LIMIT = 20;

  private final PlayerRepo playerRepo;
  private final FindPlayerMatchesRepository findPlayerMatchesRepository;

  /**
   * Finds matches for a player by their name.
   *
   * @param playerName The player's name to search for
   * @param limit Maximum number of matches to return. Defaults to 20 if null.
   * @return List of FindPlayerMatchesResponse ordered by match date descending. Returns empty list
   *     if player not found.
   */
  @Transactional(readOnly = true)
  public List<FindPlayerMatchesResponse> findPlayerMatches(String playerName, Integer limit) {

    log.info("Finding matches for player '{}' with limit={}", playerName, limit);

    // Find player by name
    Optional<PlayerDomain> playerOpt = playerRepo.findByName(playerName);

    if (playerOpt.isEmpty()) {
      log.info("Player '{}' not found", playerName);
      return List.of();
    }

    PlayerDomain player = playerOpt.get();
    Long playerId = player.getId();

    log.info("Found player '{}' with ID {}", playerName, playerId);

    // Get default player IDs
    Set<Long> defaultPlayerIds = PlayerConfiguration.DEFAULT_PLAYERS.keySet();

    // Use default limit if not specified
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    // Find matches with statistics
    List<FindPlayerMatchesResponse> matches =
        findPlayerMatchesRepository.findPlayerMatches(
            playerId, playerName, defaultPlayerIds, effectiveLimit);

    log.info("Found {} matches for player '{}'", matches.size(), playerName);

    return matches;
  }
}
