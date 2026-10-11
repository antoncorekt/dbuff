package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReplayParseRunRepo extends JpaRepository<ReplayParseRunDomain, Long> {

  Optional<ReplayParseRunDomain> findByMatchIdAndParserVersion(Long matchId, Integer parserVersion);

  /** Runs of this parser version that may be tried again: due retries and abandoned leases. */
  @Query(
      """
      select r from ReplayParseRunDomain r
      where r.parserVersion = :version
        and ((r.status in ('FAILED_DOWNLOAD', 'FAILED_RESOURCE') and r.nextAttemptAt <= :now)
             or (r.status = 'RUNNING' and r.leaseUntil < :now))
      order by r.id
      """)
  List<ReplayParseRunDomain> findDueRetries(
      @Param("version") Integer version, @Param("now") LocalDateTime now);

  @Transactional
  @Modifying
  @Query("delete from ReplayParseRunDomain r where r.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
