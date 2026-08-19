package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for querying matches where a focus group of players met an external player, either as
 * teammates or as opponents.
 *
 * <p>Extends the {@link PlayerMatchStatisticDomain} JPA repository so that standard statistic
 * lookups remain available alongside the custom aggregation query below.
 */
@Repository
public interface ExternalPlayerStatisticRepository
    extends JpaRepository<PlayerMatchStatisticDomain, Long> {

  /**
   * Finds, for every match in which at least one focus player and the external player both
   * participated, one row per (match, focus player) pairing.
   *
   * <p>Each returned {@code Object[]} row contains, in order:
   *
   * <ol>
   *   <li>matchId ({@code Long})
   *   <li>focus player's win flag ({@code Long}, 1 = win, 0 = loss)
   *   <li>focus player's team ({@code Boolean} isRadiant)
   *   <li>external player's team ({@code Boolean} isRadiant)
   *   <li>external player's hero pretty name ({@code String})
   *   <li>external player's playerSlot ({@code Long}) — used to look up abilities
   *   <li>match date ({@code LocalDate}, may be {@code null})
   * </ol>
   *
   * <p>A focus player that is also the external player is excluded from the pairing so a player is
   * never matched against themselves. Rows are ordered by match date descending (most recent
   * first); a match containing multiple focus players yields multiple rows, which callers
   * deduplicate to a single entry per match (valid because all focus players always share the same
   * team).
   *
   * @param focusPlayerIds the focus group account IDs
   * @param externalPlayerId the external player's account ID
   * @return list of rows ordered by match date descending
   */
  @Query(
      """
      SELECT focus.matchId, focus.win, focus.isRadiant, ext.isRadiant,
             ext.heroPrettyName, ext.playerSlot, m.startLocalDate
      FROM PlayerMatchStatisticDomain focus
      JOIN PlayerMatchStatisticDomain ext
        ON focus.matchId = ext.matchId
      LEFT JOIN MatchDomain m
        ON m.id = focus.matchId
      WHERE focus.playerId IN :focusPlayerIds
        AND ext.playerId = :externalPlayerId
        AND focus.playerSlot <> ext.playerSlot
      ORDER BY m.startLocalDate DESC NULLS LAST, focus.matchId DESC
      """)
  List<Object[]> findFocusVsExternalRows(
      @Param("focusPlayerIds") Collection<Long> focusPlayerIds,
      @Param("externalPlayerId") Long externalPlayerId);
}
