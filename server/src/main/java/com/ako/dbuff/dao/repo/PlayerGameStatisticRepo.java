package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerGameStatisticRepo
    extends JpaRepository<PlayerMatchStatisticDomain, PlayerGameStatisticDomainId> {

  List<PlayerMatchStatisticDomain> findAllByMatchId(long matchId);

  /**
   * Find player statistics by matchId and playerId.
   * Used when we don't have the playerSlot (e.g., when scraping from Dotabuff).
   * 
   * @param matchId the match ID
   * @param playerId the player account ID
   * @return the player statistics if found
   */
  Optional<PlayerMatchStatisticDomain> findByMatchIdAndPlayerId(Long matchId, Long playerId);
}
