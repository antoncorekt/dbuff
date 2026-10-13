package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The stored lane assignments of the heroes that have a lane and a window. */
@Component
@RequiredArgsConstructor
class StoredLaneSlots {
  private final LaneAssignmentRepo laneRepo;

  List<LaneSlot> of(long matchId) {
    List<LaneSlot> lanes = new ArrayList<>();
    for (LaneAssignmentDomain a : laneRepo.findByMatchId(matchId)) {
      Zone.Lane lane = a.getLane() == null ? null : Enums.find(Zone.Lane.class, a.getLane());
      if (lane != null
          && a.getPlayerSlot() != null
          && a.getStartSeconds() != null
          && a.getEndSeconds() != null) {
        lanes.add(
            new LaneSlot(
                a.getPlayerSlot(),
                TeamSide.ofSlot(a.getPlayerSlot()),
                lane,
                a.getStartSeconds(),
                a.getEndSeconds()));
      }
    }
    return lanes;
  }
}
