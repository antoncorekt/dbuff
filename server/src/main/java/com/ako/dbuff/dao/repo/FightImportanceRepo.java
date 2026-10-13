package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.FightImportanceDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FightImportanceRepo extends JpaRepository<FightImportanceDomain, Long> {

  List<FightImportanceDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from FightImportanceDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
