package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import java.util.List;

/** What a hero did over the whole game, to tell whether a stretch of it was undisturbed. */
public record HeroPast(Trace trace, List<AwayTrip> trips) {
  /** No other trip of his overlaps the stretch and he was alive through all of it. */
  public boolean undisturbed(AwayTrip trip, double from, double to) {
    boolean otherTrip =
        trips.stream()
            .filter(t -> t.startSeconds() != trip.startSeconds())
            .anyMatch(t -> t.startSeconds() < to && t.endSeconds() > from);
    return !otherTrip && aliveThrough(from, to);
  }

  private boolean aliveThrough(double from, double to) {
    if (trace == null) {
      return true;
    }
    for (int i = trace.indexAtOrAfter(from); i <= trace.indexAt(to); i++) {
      if ((trace.flags()[i] & 1) == 0) {
        return false;
      }
    }
    return true;
  }
}
