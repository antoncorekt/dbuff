package com.ako.dbuff.service.replay.analysis.role;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** The two teams of a match; the stored player slots 0-4 are Radiant and 128-132 are Dire. */
public enum TeamSide {
  RADIANT,
  DIRE;

  public static final long FIRST_DIRE_SLOT = 128;

  public TeamSide opponent() {
    return this == RADIANT ? DIRE : RADIANT;
  }

  public static TeamSide ofSlot(long playerSlot) {
    return playerSlot >= FIRST_DIRE_SLOT ? DIRE : RADIANT;
  }

  /**
   * The side all the slots are on; null when there are none, they are on both sides, or the set is
   * null. A null slot is ignored.
   */
  public static TeamSide commonSide(Set<Long> playerSlots) {
    if (playerSlots == null) {
      return null;
    }
    Set<TeamSide> sides =
        playerSlots.stream()
            .filter(Objects::nonNull)
            .map(TeamSide::ofSlot)
            .collect(Collectors.toSet());
    return sides.size() == 1 ? sides.iterator().next() : null;
  }
}
