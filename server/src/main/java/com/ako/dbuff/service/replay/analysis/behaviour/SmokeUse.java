package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;

/**
 * One use of a Smoke of Deceit.
 *
 * @param group the slots of the heroes that held the modifier, the user included, in order
 * @param endSeconds when the smoke ended for the group: the first time it broke for a hero, else
 *     when the last modifier ran out
 * @param startZone where the group stood at the start, null when no hero of it had a track sample
 * @param endZone where the group stood at the end, null likewise
 */
public record SmokeUse(
    TeamSide team,
    long userSlot,
    List<Long> group,
    int startSeconds,
    int endSeconds,
    End end,
    Zone startZone,
    Zone endZone,
    SmokeResult result) {

  public enum End {
    EXPIRED("ran out"),
    BROKEN("broke");

    private final String words;

    End(String words) {
      this.words = words;
    }

    public String words() {
      return words;
    }
  }
}
