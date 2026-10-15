package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the mid analysis reads from the stored tables for one match.
 *
 * @param windowFights the fights that began in the mid-game window
 * @param joinedIndexes per player slot, the indexes of the window fights he took part in
 * @param teamOf the team of each slot of the roster
 */
record MidInputs(
    MidTally tally,
    Map<Integer, Trace> traces,
    Map<Long, List<ActivitySegment>> activity,
    List<TeamRotationDomain> rotations,
    List<FightDomain> windowFights,
    Map<Long, Set<Integer>> joinedIndexes,
    MidIncome income,
    Map<Long, TeamSide> teamOf,
    int windowStart,
    int windowEnd) {

  List<FightDomain> fightsJoinedBy(long slot) {
    Set<Integer> joined = joinedIndexes.getOrDefault(slot, Set.of());
    return windowFights.stream().filter(f -> joined.contains(f.getFightIndex())).toList();
  }
}
