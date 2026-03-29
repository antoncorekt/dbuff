package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.id.KillLogId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface KillLogRepo extends JpaRepository<KillLogDomain, KillLogId> {

  /**
   * Find all kill logs for a specific match.
   *
   * @param matchId the match ID
   * @return list of kill log entries for the match
   */
  List<KillLogDomain> findAllByMatchId(@Param("matchId") Long matchId);

  /**
   * Find all kills made by a specific player in a match.
   *
   * @param matchId the match ID
   * @param playerSlot the player slot (0-9)
   * @return list of kill log entries for the player
   */
  List<KillLogDomain> findAllByMatchIdAndPlayerSlot(
      @Param("matchId") Long matchId, @Param("playerSlot") Long playerSlot);

  /**
   * Find all deaths of a specific player in a match.
   *
   * @param matchId the match ID
   * @param killedPlayerSlot the killed player's slot (0-9)
   * @return list of kill log entries where the player was killed
   */
  List<KillLogDomain> findAllByMatchIdAndKilledPlayerSlot(
      @Param("matchId") Long matchId, @Param("killedPlayerSlot") Long killedPlayerSlot);

  /**
   * Delete all kill logs associated with a specific match.
   *
   * @param matchId the match ID
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM KillLogDomain k WHERE k.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);
}
