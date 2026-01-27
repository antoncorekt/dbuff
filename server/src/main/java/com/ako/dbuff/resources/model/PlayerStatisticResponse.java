package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for player statistics summary. Contains aggregated statistics from
 * PlayerMatchStatisticDomain for a given date range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatisticResponse {

  /** Player account ID */
  private Long playerId;

  /** Player name */
  private String playerName;

  /** Start date of the statistics period */
  private LocalDate startDate;

  /** End date of the statistics period */
  private LocalDate endDate;

  /** Total number of matches in the period */
  private Long totalMatches;

  /** N most popular heroes (default 3) */
  private List<HeroStatistic> popularHeroes;

  // Observer and Sentry wards
  private BigDecimal avgObsPlaced;
  private BigDecimal avgSenPlaced;

  // Farming statistics
  private BigDecimal avgCreepsStacked;
  private BigDecimal avgLastHits;
  private Long maxLastHits;
  private Long minLastHits;
  private BigDecimal avgDenies;
  private BigDecimal avgCampsStacked;
  private BigDecimal avgRunePickups;

  // Objective statistics
  private BigDecimal avgTowerKills;
  private BigDecimal avgRoshanKills;

  // Win/Loss statistics
  private BigDecimal avgWinRate;
  private Long wins;
  private Long losses;

  // KDA statistics
  private BigDecimal avgKda;
  private BigDecimal maxKda;
  private BigDecimal minKda;

  // Kill statistics
  private BigDecimal avgNeutralKills;
  private BigDecimal avgCourierKills;

  // Efficiency statistics
  private BigDecimal avgLaneEfficiency;
  private BigDecimal avgGoldPerMin;
  private Long maxGoldPerMin;
  private Long minGoldPerMin;
  private BigDecimal avgXpPerMin;

  /** Hero statistics for popular heroes */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HeroStatistic {
    private Long heroId;
    private String heroName;
    private String heroPrettyName;
    private Long pickCount;
    private BigDecimal winRate;
  }
}
