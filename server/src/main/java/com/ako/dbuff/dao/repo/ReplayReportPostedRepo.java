package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ReplayReportPostedDomain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReplayReportPostedRepo extends JpaRepository<ReplayReportPostedDomain, Long> {

  Optional<ReplayReportPostedDomain> findByMatchIdAndInstanceId(Long matchId, String instanceId);

  @Transactional
  @Modifying
  @Query("delete from ReplayReportPostedDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
