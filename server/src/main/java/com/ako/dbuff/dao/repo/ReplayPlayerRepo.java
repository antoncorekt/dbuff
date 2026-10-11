package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReplayPlayerRepo extends JpaRepository<ReplayPlayerDomain, Long> {

  List<ReplayPlayerDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from ReplayPlayerDomain p where p.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
