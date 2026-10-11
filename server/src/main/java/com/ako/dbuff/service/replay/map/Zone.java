package com.ako.dbuff.service.replay.map;

/**
 * What a point on the map is. {@code lane} is set for a lane; {@code camp} (an index into the
 * layout's camps) for a spot inside a camp's box; {@code half} says whose side of the river it is.
 */
public record Zone(Kind kind, Lane lane, Half half, int camp) {

  public enum Kind {
    BASE,
    LANE,
    RIVER,
    JUNGLE
  }

  public enum Lane {
    BOTTOM,
    MID,
    TOP
  }

  public enum Half {
    RADIANT,
    DIRE
  }

  public static final int NO_CAMP = -1;
}
