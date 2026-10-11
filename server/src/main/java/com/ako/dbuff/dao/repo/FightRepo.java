package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.FightDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FightRepo extends JpaRepository<FightDomain, Long> {

  @Transactional
  @Modifying
  @Query("delete from FightDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
