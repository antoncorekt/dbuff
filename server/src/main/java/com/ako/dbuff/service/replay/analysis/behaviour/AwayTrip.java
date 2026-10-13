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
    REJOINED("joined the group there"),
    FARMING("went to farm the jungle"),
    DIED("died before the trip ended"),
    BOUGHT_BACK("bought back before the trip ended"),
    TRACK_ENDED("his track ended"),
    NO_GROUP("the group broke up"),
    GAME_END("was still away when the game ended");

    private final String words;

    End(String words) {
      this.words = words;
    }

    public String words() {
      return words;
    }
  }

  public int durationSeconds() {
    return endSeconds - startSeconds;
  }
}
