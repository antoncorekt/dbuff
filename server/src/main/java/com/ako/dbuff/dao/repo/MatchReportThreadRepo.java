package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchReportStage;
import com.ako.dbuff.dao.model.MatchReportThreadDomain;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MatchReportThreadRepo extends JpaRepository<MatchReportThreadDomain, Long> {

  Optional<MatchReportThreadDomain> findByMatchIdAndInstanceId(Long matchId, String instanceId);

  @Transactional
  @Modifying
  @Query("delete from MatchReportThreadDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  /**
   * Moves a staged row to a later stage and refreshes the stage 1 facts it is given (a null leaves
   * the stored one), touching nothing else. It changes the row only while its stage is one of
   * {@code allowed}, so a stage never goes back.
   *
   * @return the rows changed, 0 when the stage was not one of {@code allowed}
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update MatchReportThreadDomain d set d.stage = :stage,"
          + " d.modeName = coalesce(:modeName, d.modeName), d.wins = coalesce(:wins, d.wins),"
          + " d.losses = coalesce(:losses, d.losses),"
          + " d.durationSeconds = coalesce(:durationSeconds, d.durationSeconds)"
          + " where d.id = :id and d.stage in :allowed")
  int advanceStage(
      @Param("id") Long id,
      @Param("stage") MatchReportStage stage,
      @Param("allowed") Collection<MatchReportStage> allowed,
      @Param("modeName") String modeName,
      @Param("wins") Integer wins,
      @Param("losses") Integer losses,
      @Param("durationSeconds") Long durationSeconds);
}
