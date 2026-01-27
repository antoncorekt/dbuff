package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persisted player statistics summary for a date range. Used for weekly/monthly statistics
 * comparison and historical tracking.
 */
@Entity
@Table(
    name = "player_statistic_summary",
    indexes = {
      @Index(name = "idx_player_stat_summary_player_id", columnList = "playerId"),
      @Index(
          name = "idx_player_stat_summary_date_range",
          columnList = "playerId, startDate, endDate")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatisticSummaryDomain {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Player account ID */
  @Column(nullable = false)
  private Long playerId;

  /** Start date of the statistics period (inclusive) */
  @Column(nullable = false)
  private LocalDate startDate;

  /** End date of the statistics period (inclusive) */
  @Column(nullable = false)
  private LocalDate endDate;

  /** When this summary was created/calculated */
  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** Total number of matches in the period */
  @Column(nullable = false)
  private Long totalMatches;

  /** Top 3 most popular heroes (comma-separated hero IDs) */
  @Column(length = 100)
  private String popularHeroIds;

  /** Top 3 most popular hero names (comma-separated) */
  @Column(length = 500)
  private String popularHeroNames;

  // Observer and Sentry wards
  @Column(precision = 10, scale = 2)
  private BigDecimal avgObsPlaced;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgSenPlaced;

  // Farming statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgCreepsStacked;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgLastHits;

  private Long maxLastHits;
  private Long minLastHits;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgDenies;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgCampsStacked;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgRunePickups;

  // Objective statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgTowerKills;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgRoshanKills;

  // Win/Loss statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgWinRate;

  private Long wins;
  private Long losses;

  // KDA statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgKda;

  @Column(precision = 10, scale = 2)
  private BigDecimal maxKda;

  @Column(precision = 10, scale = 2)
  private BigDecimal minKda;

  // Kill statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgNeutralKills;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgCourierKills;

  // Efficiency statistics
  @Column(precision = 10, scale = 2)
  private BigDecimal avgLaneEfficiency;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgGoldPerMin;

  private Long maxGoldPerMin;
  private Long minGoldPerMin;

  @Column(precision = 10, scale = 2)
  private BigDecimal avgXpPerMin;
}
