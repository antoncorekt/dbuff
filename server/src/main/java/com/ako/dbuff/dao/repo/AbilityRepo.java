package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.id.AbilityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbilityRepo extends JpaRepository<AbilityDomain, AbilityId> {

  /**
   * Delete all abilities associated with a specific match.
   *
   * @param matchId the match ID
   */
  @Modifying
  @Query("DELETE FROM AbilityDomain a WHERE a.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  List<AbilityDomain> findAllByMatchId(@Param("matchId") Long matchId);
}
