package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** When each hero died and bought back, in game seconds by player slot, earliest first. */
@Component
@RequiredArgsConstructor
class StoredEventTimes {
  private final DeathAnalysisRepo deathRepo;
  private final BuybackEventRepo buybackRepo;

  /** An Aegis revival is not a death. */
  Map<Long, List<Double>> deaths(long matchId) {
    return timesBySlot(
        deathRepo.findByMatchId(matchId).stream()
            .filter(d -> !Boolean.TRUE.equals(d.getReincarnated()))
            .map(d -> new SlotTime(d.getPlayerSlot(), d.getDeathSeconds())));
  }

  Map<Long, List<Double>> buybacks(long matchId) {
    return timesBySlot(
        buybackRepo.findByMatchId(matchId).stream()
            .map(b -> new SlotTime(b.getPlayerSlot(), b.getSeconds())));
  }

  private record SlotTime(Long slot, Double seconds) {}

  private static Map<Long, List<Double>> timesBySlot(Stream<SlotTime> times) {
    Map<Long, List<Double>> bySlot = new HashMap<>();
    times
        .filter(t -> t.slot() != null && t.seconds() != null)
        .forEach(t -> bySlot.computeIfAbsent(t.slot(), s -> new ArrayList<>()).add(t.seconds()));
    bySlot.values().forEach(list -> list.sort(Double::compare));
    return bySlot;
  }
}
