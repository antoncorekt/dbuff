package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TeamDefensePointRepo extends JpaRepository<TeamDefensePointDomain, Long> {

  List<TeamDefensePointDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from TeamDefensePointDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
