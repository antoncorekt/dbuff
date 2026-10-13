package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.map.Zone;

/**
 * A hero away from his team's group for at least the threshold.
 *
 * @param from where he stood when he left
 * @param to where he got: where he joined the team, or where he was last seen away
 * @param groupZone where the team's group was when he left
 * @param arrivalSeconds when he first stood where he got; the end when he never did
 * @param teleported a teleport ended inside the trip, so the arrival was not a walk
 */
public record AwayTrip(
    long slot,
    int startSeconds,
    int arrivalSeconds,
    int endSeconds,
    Zone from,
    Zone to,
    Zone groupZone,
    boolean teleported,
    End end) {

  public enum End {
    REJOINED("joined the group there", false),
    FARMING("went to farm the jungle", true),
    DIED("died before the trip ended", false),
    BOUGHT_BACK("bought back before the trip ended", false),
    TRACK_ENDED("his track ended", true),
    NO_GROUP("the group broke up", true),
    GAME_END("was still away when the game ended", true);

    private final String words;
    private final boolean hasTail;

    End(String words, boolean hasTail) {
      this.words = words;
      this.hasTail = hasTail;
    }

    /**
     * Whether what follows the trip still belongs to it; after a rejoin, a death or a buyback it is
     * the group's, or nobody's.
     */
    public boolean hasTail() {
      return hasTail;
    }

    public String words() {
      return words;
    }
  }

  public int durationSeconds() {
    return endSeconds - startSeconds;
  }
}
