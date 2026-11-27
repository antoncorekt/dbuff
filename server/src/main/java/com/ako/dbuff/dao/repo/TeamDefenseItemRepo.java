package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TeamDefenseItemRepo extends JpaRepository<TeamDefenseItemDomain, Long> {

  List<TeamDefenseItemDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from TeamDefenseItemDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
