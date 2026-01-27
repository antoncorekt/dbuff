package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.model.PlayerStatisticSummaryDomain;
import com.ako.dbuff.dao.repo.PlayerStatisticRepository;
import com.ako.dbuff.dao.repo.PlayerStatisticSummaryRepo;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.resources.model.PlayerStatisticResponse.HeroStatistic;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating and persisting player statistics. Provides aggregated statistics from
 * PlayerMatchStatisticDomain and supports weekly/monthly comparisons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerStatisticService {

  private static final int DEFAULT_HERO_LIMIT = 3;

  private final PlayerStatisticRepository playerStatisticRepository;
  private final PlayerStatisticSummaryRepo playerStatisticSummaryRepo;

  /**
   * Gets player statistics for a specific date range.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param heroLimit Number of popular heroes to return. Defaults to 3 if null.
   * @return PlayerStatisticResponse with aggregated statistics
   */
  @Transactional(readOnly = true)
  public PlayerStatisticResponse getPlayerStatistics(
      Long playerId, LocalDate startDate, LocalDate endDate, Integer heroLimit) {

    log.info(
        "Fetching player statistics for player {} with startDate={}, endDate={}, heroLimit={}",
        playerId,
        startDate,
        endDate,
        heroLimit);

    // Use current date as default end date if not specified
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    // Use default hero limit if not specified
    int effectiveHeroLimit = heroLimit != null && heroLimit > 0 ? heroLimit : DEFAULT_HERO_LIMIT;

    PlayerStatisticResponse statistics =
        playerStatisticRepository.findPlayerStatistics(
            playerId, startDate, effectiveEndDate, effectiveHeroLimit);

    log.info(
        "Found statistics for player {} with {} total matches",
        playerId,
        statistics.getTotalMatches());

    return statistics;
  }

  /**
   * Persists player statistics for a specific date range. This is useful for weekly/monthly
   * statistics comparison.
   *
   * @param playerId The player's account ID
   * @param startDate Start date of the period
   * @param endDate End date of the period
   * @param heroLimit Number of popular heroes to include
   * @return The persisted PlayerStatisticSummaryDomain
   */
  @Transactional
  public PlayerStatisticSummaryDomain persistPlayerStatistics(
      Long playerId, LocalDate startDate, LocalDate endDate, Integer heroLimit) {

    log.info(
        "Persisting player statistics for player {} with startDate={}, endDate={}",
        playerId,
        startDate,
        endDate);

    // Check if summary already exists for this date range
    Optional<PlayerStatisticSummaryDomain> existing =
        playerStatisticSummaryRepo.findByPlayerIdAndStartDateAndEndDate(
            playerId, startDate, endDate);

    if (existing.isPresent()) {
      log.info(
          "Statistics summary already exists for player {} and date range {} to {}",
          playerId,
          startDate,
          endDate);
      return existing.get();
    }

    // Calculate statistics
    PlayerStatisticResponse stats = getPlayerStatistics(playerId, startDate, endDate, heroLimit);

    // Convert to domain entity
    PlayerStatisticSummaryDomain summary = convertToSummaryDomain(stats);

    // Persist
    PlayerStatisticSummaryDomain saved = playerStatisticSummaryRepo.save(summary);

    log.info(
        "Persisted statistics summary with id {} for player {} and date range {} to {}",
        saved.getId(),
        playerId,
        startDate,
        endDate);

    return saved;
  }

  /**
   * Gets persisted statistics summaries for a player.
   *
   * @param playerId The player's account ID
   * @return List of persisted summaries ordered by start date descending
   */
  @Transactional(readOnly = true)
  public List<PlayerStatisticSummaryDomain> getPersistedStatistics(Long playerId) {
    return playerStatisticSummaryRepo.findByPlayerIdOrderByStartDateDesc(playerId);
  }

  /**
   * Gets persisted statistics summaries for a player within a date range.
   *
   * @param playerId The player's account ID
   * @param startDate Start date to search from
   * @param endDate End date to search to
   * @return List of persisted summaries within the date range
   */
  @Transactional(readOnly = true)
  public List<PlayerStatisticSummaryDomain> getPersistedStatistics(
      Long playerId, LocalDate startDate, LocalDate endDate) {
    return playerStatisticSummaryRepo.findByPlayerIdAndDateRange(playerId, startDate, endDate);
  }

  /**
   * Persists weekly statistics for a player. Calculates statistics for the last 7 days.
   *
   * @param playerId The player's account ID
   * @return The persisted summary
   */
  @Transactional
  public PlayerStatisticSummaryDomain persistWeeklyStatistics(Long playerId) {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(7);
    return persistPlayerStatistics(playerId, startDate, endDate, DEFAULT_HERO_LIMIT);
  }

  /** Converts PlayerStatisticResponse to PlayerStatisticSummaryDomain for persistence. */
  private PlayerStatisticSummaryDomain convertToSummaryDomain(PlayerStatisticResponse stats) {
    // Convert popular heroes to comma-separated strings
    String heroIds =
        stats.getPopularHeroes().stream()
            .map(h -> String.valueOf(h.getHeroId()))
            .collect(Collectors.joining(","));

    String heroNames =
        stats.getPopularHeroes().stream()
            .map(HeroStatistic::getHeroPrettyName)
            .collect(Collectors.joining(","));

    return PlayerStatisticSummaryDomain.builder()
        .playerId(stats.getPlayerId())
        .startDate(stats.getStartDate())
        .endDate(stats.getEndDate())
        .createdAt(LocalDateTime.now())
        .totalMatches(stats.getTotalMatches())
        .popularHeroIds(heroIds)
        .popularHeroNames(heroNames)
        // Observer and Sentry wards
        .avgObsPlaced(stats.getAvgObsPlaced())
        .avgSenPlaced(stats.getAvgSenPlaced())
        // Farming statistics
        .avgCreepsStacked(stats.getAvgCreepsStacked())
        .avgLastHits(stats.getAvgLastHits())
        .maxLastHits(stats.getMaxLastHits())
        .minLastHits(stats.getMinLastHits())
        .avgDenies(stats.getAvgDenies())
        .avgCampsStacked(stats.getAvgCampsStacked())
        .avgRunePickups(stats.getAvgRunePickups())
        // Objective statistics
        .avgTowerKills(stats.getAvgTowerKills())
        .avgRoshanKills(stats.getAvgRoshanKills())
        // Win/Loss statistics
        .avgWinRate(stats.getAvgWinRate())
        .wins(stats.getWins())
        .losses(stats.getLosses())
        // KDA statistics
        .avgKda(stats.getAvgKda())
        .maxKda(stats.getMaxKda())
        .minKda(stats.getMinKda())
        // Kill statistics
        .avgNeutralKills(stats.getAvgNeutralKills())
        .avgCourierKills(stats.getAvgCourierKills())
        // Efficiency statistics
        .avgLaneEfficiency(stats.getAvgLaneEfficiency())
        .avgGoldPerMin(stats.getAvgGoldPerMin())
        .maxGoldPerMin(stats.getMaxGoldPerMin())
        .minGoldPerMin(stats.getMinGoldPerMin())
        .avgXpPerMin(stats.getAvgXpPerMin())
        .build();
  }
}
