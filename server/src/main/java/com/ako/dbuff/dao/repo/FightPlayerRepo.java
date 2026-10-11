package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FightPlayerRepo extends JpaRepository<FightPlayerDomain, Long> {

  @Transactional
  @Modifying
  @Query("delete from FightPlayerDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
