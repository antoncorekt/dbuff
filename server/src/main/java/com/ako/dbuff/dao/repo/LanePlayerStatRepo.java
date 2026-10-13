package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.LanePlayerStatDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LanePlayerStatRepo extends JpaRepository<LanePlayerStatDomain, Long> {

  List<LanePlayerStatDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from LanePlayerStatDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
