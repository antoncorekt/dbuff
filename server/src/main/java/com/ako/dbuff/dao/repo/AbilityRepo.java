package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.id.AbilityId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AbilityRepo extends JpaRepository<AbilityDomain, AbilityId> {

  @Transactional
  @Modifying
  @Query("DELETE FROM AbilityDomain a WHERE a.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  List<AbilityDomain> findAllByMatchId(@Param("matchId") Long matchId);

  @Query(
      """
      SELECT p.win, COUNT(a)
      FROM AbilityDomain a
      JOIN PlayerMatchStatisticDomain p
        ON a.matchId = p.matchId AND a.playerSlot = p.playerSlot
      JOIN MatchDomain m ON a.matchId = m.id
      WHERE a.playerId = :playerId
        AND a.abilityId = :abilityId
        AND a.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      GROUP BY p.win
      """)
  List<Object[]> countWinLossByPlayerAbility(
      @Param("playerId") Long playerId,
      @Param("abilityId") Long abilityId,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);

  @Query(
      """
      SELECT a.abilityId, a.name, a.prettyName,
             AVG(a.damageDealt), AVG(a.damageReceived), AVG(a.useCount),
             COUNT(a)
      FROM AbilityDomain a
      WHERE a.playerId = :playerId
        AND a.matchId != :excludeMatchId
        AND a.abilityId IN (
          SELECT a2.abilityId FROM AbilityDomain a2
          WHERE a2.matchId = :currentMatchId AND a2.playerId = :playerId
        )
      GROUP BY a.abilityId, a.name, a.prettyName
      """)
  List<Object[]> findAvgStatsByPlayerAbilities(
      @Param("playerId") Long playerId,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("currentMatchId") Long currentMatchId);
}
