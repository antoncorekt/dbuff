package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for KillLogDomain.
 *
 * <p>Uses playerSlot instead of playerId because: - Anonymous players have null accountId (stored
 * as -1 in playerId) - Multiple anonymous players in the same match would have the same playerId -
 * playerSlot (0-9) is always unique within a match
 *
 * <p>The composite key includes: - matchId: the match this kill occurred in - playerSlot: the
 * killer's slot (0-9) - time: the game time in seconds when the kill occurred - killedHeroName:
 * the internal name of the killed hero (e.g., "npc_dota_hero_monkey_king")
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KillLogId implements Serializable {
  private Long matchId;
  private Long playerSlot;
  private Long time;
  private String killedHeroName;
}
