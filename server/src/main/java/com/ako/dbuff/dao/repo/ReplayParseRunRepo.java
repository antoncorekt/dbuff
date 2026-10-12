package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReplayParseRunRepo extends JpaRepository<ReplayParseRunDomain, Long> {

  Optional<ReplayParseRunDomain> findByMatchIdAndParserVersion(Long matchId, Integer parserVersion);

  List<ReplayParseRunDomain> findByParserVersionAndStatusInOrderByMatchId(
      Integer parserVersion, java.util.Collection<ReplayStatus> statuses);

  /**
   * Runs of this parser version that finished with analysis stored since the given time: DONE, and
   * PARTIAL only once no retry is left, so a report is not posted from data a retry may complete.
   */
  @Query(
      """
      select r from ReplayParseRunDomain r
      where r.parserVersion = :version
        and (r.status = 'DONE' or (r.status = 'PARTIAL' and r.nextAttemptAt is null))
        and r.finishedAt >= :since
      order by r.finishedAt
      """)
  List<ReplayParseRunDomain> findFinishedSince(
      @Param("version") Integer version, @Param("since") LocalDateTime since);

  String DUE_RETRY =
      """
      select m from ReplayParseRunDomain r, MatchDomain m
      where m.id = r.matchId
        and r.parserVersion = :version
        and m.gameModeId in :modes
        and ((r.status in ('FAILED_DOWNLOAD', 'FAILED_RESOURCE', 'FAILED_TIMEOUT', 'PARTIAL')
              and r.nextAttemptAt <= :now)
             or (r.status = 'RUNNING' and r.leaseUntil < :now and r.attempts < :maxAttempts))
      """;

  String NEWEST_FIRST = " and m.id not in :excluded order by m.startTime desc";

  /**
   * Due retries of matches mapped within the fresh window that also started after {@code
   * startedAfter}; abandoned leases count as due. Only matches of the given modes are listed.
   */
  @Query(
      DUE_RETRY
          + " and m.detailsMappedAt >= :freshAfter and m.startTime > :startedAfter"
          + NEWEST_FIRST)
  List<MatchDomain> findDueFreshRetries(
      @Param("version") Integer version,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") Integer maxAttempts,
      @Param("freshAfter") LocalDateTime freshAfter,
      @Param("startedAfter") Long startedAfter,
      @Param("modes") Collection<Long> modes,
      @Param("excluded") Collection<Long> excluded,
      Pageable pageable);

  /** Due retries of matches mapped within the young window that have few attempts. */
  @Query(
      DUE_RETRY
          + " and m.detailsMappedAt >= :youngAfter and r.attempts <= :youngAttempts"
          + NEWEST_FIRST)
  List<MatchDomain> findDueYoungRetries(
      @Param("version") Integer version,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") Integer maxAttempts,
      @Param("youngAfter") LocalDateTime youngAfter,
      @Param("youngAttempts") Integer youngAttempts,
      @Param("modes") Collection<Long> modes,
      @Param("excluded") Collection<Long> excluded,
      Pageable pageable);

  /** Due retries older than the young window or with many attempts. */
  @Query(
      DUE_RETRY
          + " and (m.detailsMappedAt < :youngAfter or r.attempts > :youngAttempts)"
          + NEWEST_FIRST)
  List<MatchDomain> findDueOldRetries(
      @Param("version") Integer version,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") Integer maxAttempts,
      @Param("youngAfter") LocalDateTime youngAfter,
      @Param("youngAttempts") Integer youngAttempts,
      @Param("modes") Collection<Long> modes,
      @Param("excluded") Collection<Long> excluded,
      Pageable pageable);

  @Query(
      """
      select r from ReplayParseRunDomain r
      where r.parserVersion = :version
        and r.status = 'RUNNING'
        and r.leaseUntil < :now
        and r.attempts >= :maxAttempts
      """)
  List<ReplayParseRunDomain> findAbandonedWithoutAttemptsLeft(
      @Param("version") Integer version,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") Integer maxAttempts);

  @Transactional
  @Modifying
  @Query("delete from ReplayParseRunDomain r where r.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
