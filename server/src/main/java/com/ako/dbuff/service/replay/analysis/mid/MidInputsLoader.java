package com.ako.dbuff.service.replay.analysis.mid;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourSplitAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the stored tables the mid analysis needs. The mid-game window runs from the end of the lane
 * stage (the fallback minute without lane results) to the end of the match.
 */
@Component
@RequiredArgsConstructor
class MidInputsLoader {
  private static final int MINUTE = 60;

  private final MatchRepo matchRepo;
  private final ReplayPlayerRepo playerRepo;
  private final LaneResultRepo laneResultRepo;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final TeamRotationRepo rotationRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final StoredActivity storedActivity;
  private final PlayerTraces playerTraces;
  private final BehaviourProperties behaviourProperties;

  MidInputs load(long matchId, MidTally tally) {
    List<PlayerTimelineDomain> timeline = timelineRepo.findByMatchId(matchId);
    int start =
        BehaviourSplitAnalyzer.startSeconds(
            laneResultRepo.findByMatchId(matchId), behaviourProperties);
    int end = windowEnd(matchRepo.findById(matchId).orElse(null), timeline);
    List<FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getStartSeconds() != null)
            .filter(f -> f.getStartSeconds() >= start && f.getStartSeconds() < end)
            .toList();
    return new MidInputs(
        tally,
        playerTraces.load(matchId),
        storedActivity.bySlot(matchId),
        rotationRepo.findByMatchId(matchId),
        fights,
        joinedIndexes(matchId, fights),
        new MidIncome(timeline),
        teams(matchId),
        start,
        end);
  }

  private Map<Long, Set<Integer>> joinedIndexes(long matchId, List<FightDomain> fights) {
    Set<Integer> inWindow =
        fights.stream().map(FightDomain::getFightIndex).collect(Collectors.toSet());
    return fightPlayerRepo.findByMatchId(matchId).stream()
        .filter(p -> Presence.ACTIVE.name().equals(p.getPresence()))
        .filter(p -> p.getPlayerSlot() != null && inWindow.contains(p.getFightIndex()))
        .collect(
            Collectors.groupingBy(
                FightPlayerDomain::getPlayerSlot,
                Collectors.mapping(
                    FightPlayerDomain::getFightIndex, Collectors.toCollection(HashSet::new))));
  }

  private Map<Long, TeamSide> teams(long matchId) {
    return playerRepo.findByMatchId(matchId).stream()
        .map(ReplayPlayerDomain::getPlayerSlot)
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(slot -> slot, TeamSide::ofSlot, (a, b) -> a));
  }

  private static int windowEnd(MatchDomain match, List<PlayerTimelineDomain> timeline) {
    if (match != null && match.getDuration() != null) {
      return match.getDuration().intValue();
    }
    return timeline.stream()
            .map(PlayerTimelineDomain::getGameMinute)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0)
        * MINUTE;
  }
}
