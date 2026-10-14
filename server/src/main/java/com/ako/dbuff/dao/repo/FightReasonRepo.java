package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.FightReasonDomain;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FightReasonRepo extends JpaRepository<FightReasonDomain, Long> {

  List<FightReasonDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from FightReasonDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  @Transactional
  @Modifying
  @Query("delete from FightReasonDomain d where d.matchId = :matchId and d.source in :sources")
  void deleteByMatchIdAndSourceIn(
      @Param("matchId") Long matchId, @Param("sources") Collection<String> sources);
}
