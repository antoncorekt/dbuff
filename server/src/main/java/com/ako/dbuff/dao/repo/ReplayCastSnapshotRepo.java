package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ReplayCastSnapshotDomain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReplayCastSnapshotRepo extends JpaRepository<ReplayCastSnapshotDomain, Long> {

  Optional<ReplayCastSnapshotDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from ReplayCastSnapshotDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
