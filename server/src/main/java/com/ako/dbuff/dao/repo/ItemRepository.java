package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.id.ItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ItemRepository extends JpaRepository<ItemDomain, ItemId> {

  @Transactional
  @Modifying
  @Query("DELETE FROM ItemDomain i WHERE i.matchId = :matchId")
  void deleteByMatchId(@Param("matchId") Long matchId);

  List<ItemDomain> findAllByMatchId(@Param("matchId") Long matchId);

  @Query(
      """
      SELECT p.win, COUNT(i)
      FROM ItemDomain i
      JOIN PlayerMatchStatisticDomain p
        ON i.matchId = p.matchId AND i.playerSlot = p.playerSlot
      JOIN MatchDomain m ON i.matchId = m.id
      WHERE i.playerId = :playerId
        AND p.heroId = :heroId
        AND i.itemId = :itemId
        AND i.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      GROUP BY p.win
      """)
  List<Object[]> countWinLossByPlayerHeroItem(
      @Param("playerId") Long playerId,
      @Param("heroId") Long heroId,
      @Param("itemId") Long itemId,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);

  @Query(
      """
      SELECT i.itemId, i.itemName, i.itemPrettyName,
             AVG(i.damageDealt), AVG(i.damageReceived), AVG(i.useCount),
             COUNT(i)
      FROM ItemDomain i
      JOIN PlayerMatchStatisticDomain p
        ON i.matchId = p.matchId AND i.playerSlot = p.playerSlot
      JOIN MatchDomain m ON i.matchId = m.id
      WHERE i.playerId = :playerId
        AND p.heroId = :heroId
        AND i.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      GROUP BY i.itemId, i.itemName, i.itemPrettyName
      """)
  List<Object[]> findAvgStatsByPlayerAndHero(
      @Param("playerId") Long playerId,
      @Param("heroId") Long heroId,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);

  @Query(
      """
      SELECT i.matchId, i.itemId, i.itemPurchaseTime, p.win
      FROM ItemDomain i
      JOIN PlayerMatchStatisticDomain p
        ON i.matchId = p.matchId AND i.playerSlot = p.playerSlot
      JOIN MatchDomain m ON i.matchId = m.id
      WHERE i.playerId = :playerId
        AND i.itemId IN :itemIds
        AND i.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      """)
  List<Object[]> findItemRecordsForPlayer(
      @Param("playerId") Long playerId,
      @Param("itemIds") List<Long> itemIds,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);

  /**
   * Every non-neutral purchase by the hero in the role, with the buyer's net worth lead over the
   * opposing team's average {@code afterMinutes} after the purchase (null when the timeline lacks
   * it). A match without a role row counts when the player's lane role is {@code laneRole}.
   */
  @Query(
      """
      SELECT i.itemId, i.itemPurchaseTime, p.win, t.networth - AVG(o.networth)
      FROM ItemDomain i
      JOIN PlayerMatchStatisticDomain p
        ON i.matchId = p.matchId AND i.playerSlot = p.playerSlot
      JOIN MatchDomain m ON i.matchId = m.id
      LEFT JOIN PlayerRoleDomain r
        ON r.matchId = i.matchId AND r.playerSlot = i.playerSlot
      LEFT JOIN PlayerTimelineDomain t
        ON t.matchId = i.matchId AND t.playerSlot = i.playerSlot
        AND t.gameMinute = FLOOR(i.itemPurchaseTime / 60.0) + :afterMinutes
      LEFT JOIN PlayerTimelineDomain o
        ON o.matchId = i.matchId AND o.gameMinute = t.gameMinute
        AND ((t.playerSlot < 128 AND o.playerSlot >= 128)
          OR (t.playerSlot >= 128 AND o.playerSlot < 128))
      WHERE p.heroId = :heroId
        AND i.isNeutral = false
        AND (r.role = :role OR (r.role IS NULL AND p.laneRole = :laneRole))
        AND i.itemPurchaseTime BETWEEN :fromSeconds AND :toSeconds
        AND i.matchId != :excludeMatchId
        AND m.startTime >= :sinceEpoch
      GROUP BY i.matchId, i.playerSlot, i.itemId, i.itemPurchaseTime, p.win, t.networth
      """)
  List<Object[]> findHeroPurchases(
      @Param("heroId") Long heroId,
      @Param("role") Integer role,
      @Param("laneRole") Long laneRole,
      @Param("fromSeconds") Long fromSeconds,
      @Param("toSeconds") Long toSeconds,
      @Param("afterMinutes") Integer afterMinutes,
      @Param("excludeMatchId") Long excludeMatchId,
      @Param("sinceEpoch") Long sinceEpoch);
}
