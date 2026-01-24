package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for PlayerMatchStatisticDomain.
 *
 * <p>Uses matchId + playerSlot instead of matchId + playerId because: - Anonymous players have null
 * accountId (stored as -1) - Multiple anonymous players in the same match would have the same
 * playerId (-1) - playerSlot (0-9) is always unique within a match
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlayerGameStatisticDomainId implements Serializable {

  private Long matchId;

  /** Player slot in the match (0-9). Slots 0-4 are Radiant, slots 5-9 (or 128-132) are Dire. */
  private Long playerSlot;
}
