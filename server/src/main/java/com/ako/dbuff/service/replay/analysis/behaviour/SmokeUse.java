package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;

/**
 * One use of a Smoke of Deceit.
 *
 * @param group the slots of the heroes that held the modifier, the user included, in order
 * @param endSeconds when the smoke ended for the group: the first time it broke for a hero, else
 *     when the last modifier ended
 * @param end UNKNOWN when no modifier broke and one has no removal or no duration in the log
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
    BROKEN("broke"),
    UNKNOWN("ended, the log does not say how");

    private final String words;

    End(String words) {
      this.words = words;
    }

    public String words() {
      return words;
    }
  }
}
