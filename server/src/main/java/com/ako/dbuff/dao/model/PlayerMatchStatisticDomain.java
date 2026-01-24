package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Player statistics for a specific match.
 *
 * <p>Primary key is (matchId, playerSlot) instead of (matchId, playerId) because: - Anonymous
 * players have null accountId (stored as -1 in playerId) - Multiple anonymous players in the same
 * match would have the same playerId - playerSlot (0-9) is always unique within a match
 */
@Entity
@IdClass(PlayerGameStatisticDomainId.class)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PlayerMatchStatisticDomain {

  @Id private Long matchId;

  /**
   * Player slot in the match (0-9). Slots 0-4 are Radiant, slots 5-9 (or 128-132 in raw format) are
   * Dire. This is part of the composite primary key.
   */
  @Id private Long playerSlot;

  /**
   * Player account ID. Can be -1 for anonymous players. Not part of the primary key to avoid
   * conflicts with multiple anonymous players.
   */
  private Long playerId;

  private Long obsPlaced;
  private Long senPlaced;
  private Long creepsStacked;
  private Long lastHits;
  private Long denies;
  private Long campsStacked;
  private Long runePickups;
  private Long towerKills;
  private Long roshanKills;

  private Long partySize;

  private Long heroId;
  private String heroName;
  private String heroPrettyName;

  private Long win;
  private Long totalGold;
  private Long totalXp;
  private BigDecimal kda;

  private Long abandons;

  private Long neutralKills;
  private Long courierKills;

  private Long aganim;
  private Long aganimShard;
  private Long moonshard;

  private Long lane;
  private BigDecimal laneEfficiency;
  private BigDecimal laneEfficiencyPct;

  private Long goldPerMin;
  private Long xpPerMin;

  private Boolean hasNeutralItems;
  private Boolean dotaApiItems;
  private Boolean hasItems;

  private Boolean dotaApiAbilities;
  private Boolean hasAbilities;
}
