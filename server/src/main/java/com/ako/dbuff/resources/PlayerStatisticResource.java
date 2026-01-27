package com.ako.dbuff.resources;

import com.ako.dbuff.dao.model.PlayerStatisticSummaryDomain;
import com.ako.dbuff.resources.model.PlayerStatisticResponse;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for player statistics. Provides endpoints to retrieve aggregated player statistics and
 * persist them for weekly/monthly comparisons.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/player")
public class PlayerStatisticResource {

  private final PlayerStatisticService playerStatisticService;

  /**
   * Gets aggregated player statistics for a specific date range.
   *
   * <p>Returns aggregated statistics including:
   *
   * <ul>
   *   <li>N most popular heroes (default 3)
   *   <li>Average/max/min for various metrics (last hits, KDA, gold per min, etc.)
   *   <li>Win/loss statistics
   *   <li>Ward placement statistics
   *   <li>Objective statistics (tower kills, roshan kills)
   * </ul>
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param heroLimit Number of popular heroes to return. Defaults to 3 if null.
   * @return PlayerStatisticResponse with aggregated statistics
   */
  @GetMapping("/{playerId}/statistic")
  public PlayerStatisticResponse getPlayerStatistics(
      @PathVariable Long playerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) Integer heroLimit) {

    log.info(
        "GET /api/v1/player/{}/statistic - startDate={}, endDate={}, heroLimit={}",
        playerId,
        startDate,
        endDate,
        heroLimit);

    return playerStatisticService.getPlayerStatistics(playerId, startDate, endDate, heroLimit);
  }

  /**
   * Persists player statistics for a specific date range. This is useful for weekly/monthly
   * statistics comparison.
   *
   * @param playerId The player's account ID
   * @param startDate Start date of the period
   * @param endDate End date of the period
   * @param heroLimit Number of popular heroes to include. Defaults to 3 if null.
   * @return The persisted PlayerStatisticSummaryDomain
   */
  @PostMapping("/{playerId}/statistic/persist")
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerStatisticSummaryDomain persistPlayerStatistics(
      @PathVariable Long playerId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(required = false) Integer heroLimit) {

    log.info(
        "POST /api/v1/player/{}/statistic/persist - startDate={}, endDate={}, heroLimit={}",
        playerId,
        startDate,
        endDate,
        heroLimit);

    return playerStatisticService.persistPlayerStatistics(playerId, startDate, endDate, heroLimit);
  }

  /**
   * Persists weekly statistics for a player. Calculates statistics for the last 7 days.
   *
   * @param playerId The player's account ID
   * @return The persisted summary
   */
  @PostMapping("/{playerId}/statistic/persist/weekly")
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerStatisticSummaryDomain persistWeeklyStatistics(@PathVariable Long playerId) {

    log.info("POST /api/v1/player/{}/statistic/persist/weekly", playerId);

    return playerStatisticService.persistWeeklyStatistics(playerId);
  }

  /**
   * Gets all persisted statistics summaries for a player.
   *
   * @param playerId The player's account ID
   * @return List of persisted summaries ordered by start date descending
   */
  @GetMapping("/{playerId}/statistic/history")
  public List<PlayerStatisticSummaryDomain> getPersistedStatistics(@PathVariable Long playerId) {

    log.info("GET /api/v1/player/{}/statistic/history", playerId);

    return playerStatisticService.getPersistedStatistics(playerId);
  }

  /**
   * Gets persisted statistics summaries for a player within a date range.
   *
   * @param playerId The player's account ID
   * @param startDate Start date to search from
   * @param endDate End date to search to
   * @return List of persisted summaries within the date range
   */
  @GetMapping("/{playerId}/statistic/history/range")
  public List<PlayerStatisticSummaryDomain> getPersistedStatisticsInRange(
      @PathVariable Long playerId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    log.info(
        "GET /api/v1/player/{}/statistic/history/range - startDate={}, endDate={}",
        playerId,
        startDate,
        endDate);

    return playerStatisticService.getPersistedStatistics(playerId, startDate, endDate);
  }
}
