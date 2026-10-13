package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.repo.TeamLaneSwapRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@code team_lane_swap} row for each hero whose main lane in the swap window (minutes 8
 * to 12 by default) is not the lane the lane analysis stored for him. Reads tracks and lanes.
 */
@Slf4j
@Order(92)
@Component
@RequiredArgsConstructor
public class BehaviourLaneSwapAnalyzer implements ReplayAnalyzer {
  private final MatchMapLayout matchMapLayout;
  private final SlotTraces slotTraces;
  private final StoredLaneSlots laneSlots;
  private final BehaviourProperties properties;
  private final TeamLaneSwapRepo swapRepo;

  @Override
  public String name() {
    return "lane-swaps";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "lanes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    MapLayout layout = matchMapLayout.of(matchId).orElse(null);
    if (layout == null) {
      log.info("Match {} was not played on the frozen map layout, no lane swaps stored", matchId);
      return;
    }
    Map<Long, Trace> traces = slotTraces.of(matchId);
    List<LaneSwap> swaps =
        new LaneSwaps(layout::zone, properties).find(traces, laneSlots.of(matchId));
    swapRepo.saveAll(swaps.stream().map(s -> row(matchId, s)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    swapRepo.deleteByMatchId(matchId);
  }

  private TeamLaneSwapDomain row(long matchId, LaneSwap s) {
    return TeamLaneSwapDomain.builder()
        .matchId(matchId)
        .team(s.team().name())
        .playerSlot(s.slot())
        .laneBefore(s.before().name())
        .laneAfter(s.after().name())
        .fromSeconds(s.fromSeconds())
        .toSeconds(s.toSeconds())
        .analysisVersion(version())
        .build();
  }
}
