package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.LaneResultDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LaneResultRepo extends JpaRepository<LaneResultDomain, Long> {

  List<LaneResultDomain> findByMatchId(Long matchId);

  @Transactional
  @Modifying
  @Query("delete from LaneResultDomain d where d.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
