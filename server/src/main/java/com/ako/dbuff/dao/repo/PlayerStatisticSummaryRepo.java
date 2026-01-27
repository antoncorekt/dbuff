package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerStatisticSummaryDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for persisted player statistics summaries. */
@Repository
public interface PlayerStatisticSummaryRepo
    extends JpaRepository<PlayerStatisticSummaryDomain, Long> {

  /**
   * Finds a summary for a specific player and date range.
   *
   * @param playerId The player's account ID
   * @param startDate Start date of the period
   * @param endDate End date of the period
   * @return Optional containing the summary if found
   */
  Optional<PlayerStatisticSummaryDomain> findByPlayerIdAndStartDateAndEndDate(
      Long playerId, LocalDate startDate, LocalDate endDate);

  /**
   * Finds all summaries for a player ordered by start date descending.
   *
   * @param playerId The player's account ID
   * @return List of summaries ordered by start date descending
   */
  List<PlayerStatisticSummaryDomain> findByPlayerIdOrderByStartDateDesc(Long playerId);

  /**
   * Finds summaries for a player within a date range.
   *
   * @param playerId The player's account ID
   * @param startDate Start date to search from
   * @param endDate End date to search to
   * @return List of summaries that overlap with the given date range
   */
  @Query(
      "SELECT s FROM PlayerStatisticSummaryDomain s "
          + "WHERE s.playerId = :playerId "
          + "AND s.startDate >= :startDate "
          + "AND s.endDate <= :endDate "
          + "ORDER BY s.startDate DESC")
  List<PlayerStatisticSummaryDomain> findByPlayerIdAndDateRange(
      @Param("playerId") Long playerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Checks if a summary exists for a specific player and date range.
   *
   * @param playerId The player's account ID
   * @param startDate Start date of the period
   * @param endDate End date of the period
   * @return true if a summary exists
   */
  boolean existsByPlayerIdAndStartDateAndEndDate(
      Long playerId, LocalDate startDate, LocalDate endDate);
}
