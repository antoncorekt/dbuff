package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the heroes whose main lane in the swap window differs from their stored lane. His main lane
 * is the lane zone he stood in for most of his living samples there, and he needs at least the
 * share of them in it; a hero who spent the window mostly in the jungle, the river or the base has
 * no main lane and is not a swap, nor is one whose time splits between lanes.
 */
public final class LaneSwaps {
  private final ZoneLookup zones;
  private final int fromSeconds;
  private final int toSeconds;
  private final double minShare;

  public LaneSwaps(ZoneLookup zones, BehaviourProperties properties) {
    this.zones = zones;
    this.fromSeconds = properties.getSwapFromMinute() * 60;
    this.toSeconds = properties.getSwapToMinute() * 60;
    this.minShare = properties.getSwapMinShare();
  }

  public List<LaneSwap> find(Map<Long, Trace> traces, List<LaneSlot> lanes) {
    List<LaneSwap> swaps = new ArrayList<>();
    for (LaneSlot assigned : lanes) {
      Trace trace = traces.get(assigned.slot());
      if (trace == null) {
        continue;
      }
      mainLane(trace)
          .filter(main -> main.lane() != assigned.lane())
          .ifPresent(
              main ->
                  swaps.add(
                      new LaneSwap(
                          assigned.slot(),
                          assigned.team(),
                          assigned.lane(),
                          main.lane(),
                          main.firstSeconds(),
                          main.lastSeconds())));
    }
    swaps.sort(Comparator.comparingInt(LaneSwap::fromSeconds).thenComparingLong(LaneSwap::slot));
    return swaps;
  }

  private record Main(Zone.Lane lane, int firstSeconds, int lastSeconds) {}

  private Optional<Main> mainLane(Trace trace) {
    Map<Zone.Lane, Integer> counts = new EnumMap<>(Zone.Lane.class);
    Map<Zone.Lane, int[]> span = new EnumMap<>(Zone.Lane.class);
    int living = 0;
    for (int i = 0; i < trace.x().length; i++) {
      int t = trace.startSeconds() + i * trace.stepSeconds();
      if (t < fromSeconds || t >= toSeconds || !TraceSamples.livingAt(trace, i)) {
        continue;
      }
      living++;
      Zone zone = zones.at(trace.x()[i], trace.y()[i]);
      if (zone.kind() == Zone.Kind.LANE) {
        counts.merge(zone.lane(), 1, Integer::sum);
        int[] s = span.computeIfAbsent(zone.lane(), l -> new int[] {t, t});
        s[1] = t;
      }
    }
    if (living == 0) {
      return Optional.empty();
    }
    int total = living;
    List<Map.Entry<Zone.Lane, Integer>> ranked =
        counts.entrySet().stream()
            .sorted(Map.Entry.<Zone.Lane, Integer>comparingByValue().reversed())
            .toList();
    if (ranked.isEmpty()
        || ranked.size() > 1 && ranked.get(0).getValue().equals(ranked.get(1).getValue())
        || ranked.get(0).getValue() < minShare * total) {
      return Optional.empty();
    }
    Zone.Lane lane = ranked.get(0).getKey();
    return Optional.of(new Main(lane, span.get(lane)[0], span.get(lane)[1]));
  }
}
