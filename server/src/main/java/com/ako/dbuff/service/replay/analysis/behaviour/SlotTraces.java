package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The stored tracks of a match by player slot (0-4 Radiant, 128-132 Dire). */
@Component
@RequiredArgsConstructor
class SlotTraces {
  private final PlayerTraces playerTraces;

  Map<Long, Trace> of(long matchId) {
    Map<Long, Trace> traces = new HashMap<>();
    playerTraces
        .load(matchId)
        .forEach((index, trace) -> traces.put(PlayerSlots.slot(index), trace));
    return traces;
  }
}
