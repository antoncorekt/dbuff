package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ObjectiveEventDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ObjectiveEventRepo extends JpaRepository<ObjectiveEventDomain, Long> {
  List<ObjectiveEventDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("DELETE FROM ObjectiveEventDomain e WHERE e.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
