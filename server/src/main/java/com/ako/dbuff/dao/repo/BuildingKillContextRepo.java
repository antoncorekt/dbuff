package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BuildingKillContextRepo extends JpaRepository<BuildingKillContextDomain, Long> {

  List<BuildingKillContextDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from BuildingKillContextDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
