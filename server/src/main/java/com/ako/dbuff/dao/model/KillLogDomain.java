package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.KillLogId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Kill log entry recording when a player killed another hero in a match.
 *
 * <p>Primary key uses playerSlot instead of playerId because: - Anonymous players have null
 * accountId (stored as -1 in playerId) - Multiple anonymous players in the same match would have
 * the same playerId - playerSlot (0-9) is always unique within a match
 *
 * <p>The composite key includes matchId, playerSlot, time, and killedHeroName to uniquely identify
 * each kill event.
 */
@Entity
@IdClass(KillLogId.class)
@Builder
@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillLogDomain {

  /** The match ID this kill occurred in. Part of composite primary key. */
  @Id private Long matchId;

  /** Player slot of the killer (0-9). Part of composite primary key. */
  @Id private Long playerSlot;

  /** Game time in seconds when the kill occurred. Part of composite primary key. */
  @Id private Long time;

  /**
   * Internal hero name of the killed hero (e.g., "npc_dota_hero_monkey_king"). Part of composite
   * primary key.
   */
  @Id private String killedHeroName;

  /** Player account ID of the killer. Can be -1 for anonymous players. */
  private Long playerId;

  /** Player slot of the killed player (0-9). Null if not determinable. */
  private Long killedPlayerSlot;

  /** Player account ID of the killed player. Can be -1 for anonymous players. */
  private Long killedPlayerId;

  /** Localized hero name of the killed hero (e.g., "Monkey King"). */
  private String killedHeroPrettyName;
}
