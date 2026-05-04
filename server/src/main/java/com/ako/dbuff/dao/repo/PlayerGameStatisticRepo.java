package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerGameStatisticRepo
    extends JpaRepository<PlayerMatchStatisticDomain, PlayerGameStatisticDomainId> {

  List<PlayerMatchStatisticDomain> findAllByMatchId(long matchId);

  Optional<PlayerMatchStatisticDomain> findByMatchIdAndPlayerId(Long matchId, Long playerId);

  @Modifying
  @Query("DELETE FROM PlayerMatchStatisticDomain p WHERE p.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  @Query(
      """
      SELECT AVG(p.heroDamage), AVG(p.towerDamage), AVG(p.heroHealing),
             AVG(p.damageTaken), COUNT(p)
      FROM PlayerMatchStatisticDomain p
      JOIN MatchDomain m ON p.matchId = m.id
      WHERE p.playerId = :playerId
        AND p.heroId = :heroId
        AND p.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      """)
  List<Object[]> findAvgDamageStatsByPlayerAndHero(
      @Param("playerId") Long playerId,
      @Param("heroId") Long heroId,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);
}
