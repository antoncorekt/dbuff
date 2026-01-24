package com.ako.dbuff.dao.model.id;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for ItemDomain.
 *
 * <p>Uses playerSlot instead of playerId because: - Anonymous players have null accountId (stored
 * as -1 in playerId) - Multiple anonymous players in the same match would have the same playerId -
 * playerSlot (0-9) is always unique within a match
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemId implements Serializable {
  private Long itemId;
  private Long matchId;
  private Long playerSlot;
}
