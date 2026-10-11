package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchDomain;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MatchRepo extends JpaRepository<MatchDomain, Long> {

  List<MatchDomain> findAllByEndProcessIsNull();

  /**
   * Matches whose detail rows are missing, including ones whose first fetch failed, since that
   * failure may have been temporary. A match tried before is retried only once {@code retryBefore}
   * has passed and only while it started after {@code recentAfter}, so matches OpenDota never
   * parsed do not stay in the queue forever.
   */
  @Query(
      """
      select m from MatchDomain m
      where m.detailsMappedAt is null
        and m.endProcess is not null
        and (m.detailsAttemptedAt is null
             or (m.detailsAttemptedAt < :retryBefore and m.startTime > :recentAfter))
      order by m.startTime desc
      """)
  List<MatchDomain> findMatchesWithoutDetails(
      @Param("retryBefore") LocalDateTime retryBefore,
      @Param("recentAfter") Long recentAfter,
      Pageable pageable);

  /**
   * Matches whose OpenDota detail rows are stored and that have no replay run of this parser
   * version yet, newest first. Only recent matches are listed because older replays are gone.
   */
  @Query(
      """
      select m from MatchDomain m
      where m.detailsMappedAt is not null
        and m.startTime > :recentAfter
        and not exists (
          select 1 from ReplayParseRunDomain r
          where r.matchId = m.id and r.parserVersion = :version)
      order by m.startTime desc
      """)
  List<MatchDomain> findMatchesWithoutReplayRun(
      @Param("version") Integer version, @Param("recentAfter") Long recentAfter, Pageable pageable);

  /** Writes only the detail bookkeeping columns, leaving fields other jobs own untouched. */
  @Transactional
  @Modifying
  @Query(
      """
      update MatchDomain m
      set m.hasParsed = :hasParsed,
          m.replayCluster = :replayCluster,
          m.replaySalt = :replaySalt,
          m.detailsMappedAt = :detailsMappedAt,
          m.detailsAttemptedAt = :detailsAttemptedAt
      where m.id = :id
      """)
  void updateDetailState(
      @Param("id") Long id,
      @Param("hasParsed") Boolean hasParsed,
      @Param("replayCluster") Long replayCluster,
      @Param("replaySalt") Long replaySalt,
      @Param("detailsMappedAt") LocalDateTime detailsMappedAt,
      @Param("detailsAttemptedAt") LocalDateTime detailsAttemptedAt);

  @QueryHints(value = @QueryHint(name = "org.hibernate.fetchSize", value = "100"))
  @Query("select o from MatchDomain o")
  Stream<MatchDomain> findAllStream();
}
