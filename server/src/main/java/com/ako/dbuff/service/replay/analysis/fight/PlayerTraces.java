package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.PlayerSlots;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Loads the sampled tracks of a match as {@link FightAssessor.Trace}s by player index. */
@Component
@RequiredArgsConstructor
public class PlayerTraces {
  private final ReplayPlayerTrackRepo trackRepo;

  /** A player whose track has no samples has no entry. */
  public Map<Integer, FightAssessor.Trace> load(long matchId) {
    Map<Integer, FightAssessor.Trace> traces = new HashMap<>();
    for (ReplayPlayerTrackDomain t : trackRepo.findByMatchId(matchId)) {
      if (t.getSampleCount() == null || t.getSampleCount() == 0 || t.getX().length == 0) {
        continue;
      }
      traces.put(
          PlayerSlots.index(t.getPlayerSlot()),
          new FightAssessor.Trace(
              t.getStartSeconds(),
              t.getStepSeconds(),
              t.getX(),
              t.getY(),
              t.getLevel(),
              t.getFlags(),
              t.getAttackRange()));
    }
    return traces;
  }
}
