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

  /** The match ID this statistic belongs to. Part of composite primary key. */
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

  /** Number of observer wards placed by the player. */
  private Long obsPlaced;

  /** Number of sentry wards placed by the player. */
  private Long senPlaced;

  /** Number of creeps stacked by the player. */
  private Long creepsStacked;

  /** Number of last hits at the end of the game. */
  private Long lastHits;

  /** Number of denies at the end of the game. */
  private Long denies;

  /** Number of camps stacked by the player. */
  private Long campsStacked;

  /** Number of runes picked up by the player. */
  private Long runePickups;

  /** Number of towers killed by the player. */
  private Long towerKills;

  /** Number of Roshan kills (last hit on Roshan) by the player. */
  private Long roshanKills;

  /** Size of the player's party. */
  private Long partySize;

  /** The hero ID played by this player. */
  private Long heroId;

  /** Internal hero name (e.g., "npc_dota_hero_antimage"). */
  private String heroName;

  /** Localized hero name (e.g., "Anti-Mage"). */
  private String heroPrettyName;

  /** Binary integer representing whether the player won (1) or lost (0). */
  private Long win;

  /** Total gold earned at the end of the game. */
  private Long totalGold;

  /** Total experience earned at the end of the game. */
  private Long totalXp;

  /** Kill/Death/Assist ratio. */
  private BigDecimal kda;

  /** Computed MMR estimate based on ranked matches. */
  private BigDecimal computedMmmr;

  /** Total hero healing done by the player. */
  private Long heroHealing;

  /** Total hero damage dealt by the player. */
  private Long heroDamage;

  /** Total tower damage dealt by the player. */
  private Long towerDamage;

  /** Total damage taken from enemy heroes. */
  private Long damageTaken;

  /** Number of times the player abandoned. */
  private Long abandons;

  /** Total number of neutral creeps killed. */
  private Long neutralKills;

  /** Total number of couriers killed. */
  private Long courierKills;

  /** Whether the player has Aghanim's Scepter (0 or 1). */
  private Long aganim;

  /** Whether the player has Aghanim's Shard (0 or 1). */
  private Long aganimShard;

  /** Whether the player has consumed Moon Shard (0 or 1). */
  private Long moonshard;

  /** Integer referring to which lane the hero laned in. */
  private Long lane;

  /** Lane efficiency as a decimal (0.0 - 1.0). */
  private BigDecimal laneEfficiency;

  /** Lane efficiency as a percentage (0 - 100). */
  private BigDecimal laneEfficiencyPct;

  /** Gold per minute obtained by the player. */
  private Long goldPerMin;

  /** Experience per minute obtained by the player. */
  private Long xpPerMin;

  /** Whether the player has neutral items in their inventory. */
  private Boolean hasNeutralItems;

  /** Whether items data was retrieved from Dota API. */
  private Boolean dotaApiItems;

  /** Whether the player has items data available. */
  private Boolean hasItems;

  /** Whether abilities data was retrieved from Dota API. */
  private Boolean dotaApiAbilities;

  /** Whether the player has abilities data available. */
  private Boolean hasAbilities;

  // ==================== High Priority - Core Stats ====================

  /** Number of kills by the player. */
  private Long kills;

  /** Number of deaths by the player. */
  private Long deaths;

  /** Number of assists by the player. */
  private Long assists;

  /** Level at the end of the game. */
  private Long level;

  /** Net worth at the end of the game. */
  private Long netWorth;

  /** Whether the player was on Radiant team. */
  private Boolean isRadiant;

  // ==================== Medium Priority - Performance Metrics ====================

  /** Total stun duration in seconds inflicted by the player. */
  private BigDecimal stuns;

  /** Teamfight participation percentage (0.0 - 1.0). */
  private BigDecimal teamfightParticipation;

  /** Actions per minute (APM) by the player. */
  private Long actionsPerMin;

  /** Lane role (1=Safe, 2=Mid, 3=Off, 4=Jungle). */
  private Long laneRole;

  /** Player's rank tier. Tens place indicates rank, ones place indicates stars. */
  private Long rankTier;

  /** Hero variant/facet (1-indexed). */
  private Long heroVariant;

  /** Number of buybacks used by the player. */
  private Long buybackCount;

  /** Total gold spent by the player. */
  private Long goldSpent;

  // ==================== Gold Timeline Stats ====================

  /** Gold at 5 minutes (300 seconds). Null if game ended before 5 minutes. */
  private Long goldAt5Min;

  /** Gold at 10 minutes (600 seconds). Null if game ended before 10 minutes. */
  private Long goldAt10Min;

  /** Gold at 15 minutes (900 seconds). Null if game ended before 15 minutes. */
  private Long goldAt15Min;

  /** Gold at 20 minutes (1200 seconds). Null if game ended before 20 minutes. */
  private Long goldAt20Min;

  // ==================== Last Hits Timeline Stats ====================

  /** Last hits at 5 minutes (300 seconds). Null if game ended before 5 minutes. */
  private Long lastHitsAt5Min;

  /** Last hits at 10 minutes (600 seconds). Null if game ended before 10 minutes. */
  private Long lastHitsAt10Min;

  /** Last hits at 15 minutes (900 seconds). Null if game ended before 15 minutes. */
  private Long lastHitsAt15Min;

  /** Last hits at 20 minutes (1200 seconds). Null if game ended before 20 minutes. */
  private Long lastHitsAt20Min;

  // ==================== XP Timeline Stats ====================

  /** XP at 5 minutes (300 seconds). Null if game ended before 5 minutes. */
  private Long xpAt5Min;

  /** XP at 10 minutes (600 seconds). Null if game ended before 10 minutes. */
  private Long xpAt10Min;

  /** XP at 15 minutes (900 seconds). Null if game ended before 15 minutes. */
  private Long xpAt15Min;

  /** XP at 20 minutes (1200 seconds). Null if game ended before 20 minutes. */
  private Long xpAt20Min;

  // ==================== Hand Attack Damage Stats ====================

  /**
   * Damage dealt by hand attacks (auto-attacks without abilities/items).
   * Extracted from damage_inflictor where key is "null".
   */
  private Long handDamageDealt;

  /**
   * Damage received from hand attacks (auto-attacks without abilities/items).
   * Extracted from damage_inflictor_received where key is "null".
   */
  private Long handDamageReceived;
}
