package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.UnitKillDomain;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UnitKillRepo extends MatchRowRepo<UnitKillDomain> {
  List<UnitKillDomain> findByMatchId(Long matchId);

  @Override
  @Transactional
  @Modifying
  @Query("DELETE FROM UnitKillDomain e WHERE e.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
