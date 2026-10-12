package com.ako.dbuff.service.replay.analysis.state;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Rebuilds each hero's {@link HeroStateTimeline} from the stored state changes of a match. */
@Component
@RequiredArgsConstructor
public class HeroStateTimelines {
  private final ReplayStateChangeRepo stateRepo;

  /** By player index; a hero without stored changes has no entry. */
  public Map<Integer, HeroStateTimeline> load(long matchId) {
    Map<Integer, HeroStateTimeline> states = new HashMap<>();
    for (ReplayStateChangeDomain c : stateRepo.findByMatchIdOrderByGameSeconds(matchId)) {
      states
          .computeIfAbsent(PlayerSlots.index(c.getPlayerSlot()), k -> new HeroStateTimeline())
          .add(
              new HeroStateTimeline.Change(
                  c.getGameSeconds(),
                  StateChangeKind.valueOf(c.getKind()),
                  c.getName(),
                  c.getReadyAtSeconds() == null ? 0 : c.getReadyAtSeconds(),
                  c.getAbilityLevel() == null ? 0 : c.getAbilityLevel(),
                  c.getManaCost() == null ? 0 : c.getManaCost()));
    }
    return states;
  }
}
