package com.ako.dbuff.service.ai.model;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure combining a match with all player statistics. Each match should have 10 player
 * statistics (5 Radiant + 5 Dire).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchWithPlayerStatistics {

  private MatchDomain match;
  private List<PlayerMatchStatisticDomain> playerStatistics;
  private List<AbilityDomain> abilities;
  private List<ItemDomain> items;

  /**
   * Returns the number of players in this match.
   *
   * @return the count of player statistics
   */
  public int getPlayerCount() {
    return playerStatistics != null ? playerStatistics.size() : 0;
  }

  /**
   * Checks if this match has the expected 10 players.
   *
   * @return true if there are exactly 10 player statistics
   */
  public boolean hasCompletePlayerData() {
    return getPlayerCount() == 10;
  }
}
