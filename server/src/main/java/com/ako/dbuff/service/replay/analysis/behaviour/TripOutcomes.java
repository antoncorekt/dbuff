package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;

/**
 * Tells what happened once a hero was at the destination of his trip, up to its end (and a few
 * seconds after it when it did not end in the group or a death): kills, creeps, fights.
 */
public final class TripOutcomes {
  /**
   * @param kills the times each hero killed or helped to kill an enemy hero, by slot
   */
  public record Events(
      Map<Long, List<Double>> kills, List<CreepHit> creeps, List<FightFact> fights) {}

  private final int lingerSeconds;

  public TripOutcomes(int lingerSeconds) {
    this.lingerSeconds = lingerSeconds;
  }

  public TripOutcome of(AwayTrip trip, TeamSide team, Events events) {
    double from = trip.arrivalSeconds();
    double to = tailEnd(trip);
    int kills =
        (int)
            events.kills().getOrDefault(trip.slot(), List.of()).stream()
                .filter(t -> t >= from && t <= to)
                .count();
    int creeps =
        (int)
            events.creeps().stream()
                .filter(c -> c.slot() == trip.slot() && c.seconds() >= from && c.seconds() <= to)
                .count();
    List<FightFact> taken =
        events.fights().stream()
            .filter(f -> f.present().contains(trip.slot()))
            .filter(f -> f.end() >= from && f.start() <= to)
            .toList();
    List<FightFact> won = taken.stream().filter(f -> team.name().equals(f.winner())).toList();
    return new TripOutcome(kills, creeps, taken.size(), won.size(), keyFight(taken, won));
  }

  /** The second up to which what he did still belongs to the trip. */
  public double tailEnd(AwayTrip trip) {
    return trip.endSeconds() + linger(trip);
  }

  /** A trip that joined the group or ended in a death has no tail: what follows is the group's. */
  private int linger(AwayTrip trip) {
    return switch (trip.end()) {
      case REJOINED, DIED, BOUGHT_BACK -> 0;
      default -> lingerSeconds;
    };
  }

  private static Integer keyFight(List<FightFact> taken, List<FightFact> won) {
    List<FightFact> pool = won.isEmpty() ? taken : won;
    return pool.isEmpty() ? null : pool.get(0).index();
  }
}
