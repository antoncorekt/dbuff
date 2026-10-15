package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Finds the first thing the mid hero did after level 6: a rotation, a stretch in another lane or
 * the jungle, or a fight he took part in. The earliest wins; with none he stayed mid.
 */
@RequiredArgsConstructor
class MidMoveFinder {
  static final String JUNGLE = "JUNGLE";

  private final MidActivityProperties properties;

  /**
   * @param rotations his own trips
   * @param segments his activity in time order
   * @param fightStarts when the fights he took part in began
   */
  MidMove find(
      int level6Seconds,
      List<TeamRotationDomain> rotations,
      List<ActivitySegment> segments,
      List<Double> fightStarts) {
    int until = level6Seconds + properties.getNextWindowSeconds();
    List<MidMove> moves = new ArrayList<>();
    for (TeamRotationDomain r : rotations) {
      if (r.getStartSeconds() != null && within(r.getStartSeconds(), level6Seconds, until)) {
        moves.add(new MidMove(MidNext.ROTATED, r.getStartSeconds(), r.getToLane()));
      }
    }
    for (ActivitySegment s : segments) {
      String place = awayPlace(s);
      int start = Math.max(s.startSeconds(), level6Seconds);
      if (place != null
          && s.endSeconds() - start >= properties.getMinAwaySeconds()
          && start < until) {
        moves.add(
            new MidMove(JUNGLE.equals(place) ? MidNext.JUNGLE : MidNext.ROTATED, start, place));
      }
    }
    for (double start : fightStarts) {
      if (within(start, level6Seconds, until)) {
        moves.add(new MidMove(MidNext.FIGHT, (int) start, null));
      }
    }
    return moves.stream()
        .min(Comparator.comparingInt(MidMove::seconds).thenComparing(m -> m.next().ordinal()))
        .orElse(MidMove.STAYED);
  }

  private static boolean within(double time, int from, int until) {
    return time >= from && time < until;
  }

  private static String awayPlace(ActivitySegment s) {
    if (s.zone() == Zone.Kind.JUNGLE || s.activity() == Activity.JUNGLE_FARM) {
      return JUNGLE;
    }
    boolean otherLane = s.zone() == Zone.Kind.LANE && s.lane() != null && s.lane() != Zone.Lane.MID;
    return otherLane ? s.lane().name() : null;
  }
}
