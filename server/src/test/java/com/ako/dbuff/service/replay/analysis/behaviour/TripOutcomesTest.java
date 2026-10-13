package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip.End;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.MID;
import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.TOP;
import static org.assertj.core.api.Assertions.assertThat;

class TripOutcomesTest {
  private final TripOutcomes outcomes = new TripOutcomes(15);
  private final AwayTrip trip = new AwayTrip(4L, 700, 700, 740, MID, TOP, MID, false, End.FARMING);

  @Test
  void of_countsKillsCreepsAndFightsInsideTheTripAndTheLinger() {
    TripOutcomes.Events events =
        new TripOutcomes.Events(
            Map.of(4L, List.of(690.0, 710.0, 750.0, 756.0), 1L, List.of(720.0)),
            List.of(
                new CreepHit(4L, 705, null, false),
                new CreepHit(4L, 900, null, false),
                new CreepHit(1L, 705, null, false)),
            List.of(
                new FightFact(2, 745, 770, "DIRE", Set.of(4L)),
                new FightFact(3, 745, 770, "RADIANT", Set.of(4L)),
                new FightFact(4, 745, 770, "RADIANT", Set.of(1L)),
                new FightFact(5, 780, 800, "RADIANT", Set.of(4L))));

    TripOutcome outcome = outcomes.of(trip, TeamSide.RADIANT, events);

    assertThat(outcome).isEqualTo(new TripOutcome(2, 1, 2, 1, 3));
  }

  @Test
  void of_countsOnlyWhatHappenedAfterArrivalAndNoTailAfterRejoining() {
    AwayTrip late = new AwayTrip(4L, 700, 720, 740, MID, TOP, MID, false, End.REJOINED);
    TripOutcomes.Events events =
        new TripOutcomes.Events(Map.of(4L, List.of(710.0, 730.0, 745.0)), List.of(), List.of());

    assertThat(outcomes.of(late, TeamSide.RADIANT, events).kills()).isEqualTo(1);
  }

  @Test
  void of_noEventsIsAnEmptyOutcome() {
    TripOutcome outcome =
        outcomes.of(
            trip, TeamSide.RADIANT, new TripOutcomes.Events(Map.of(), List.of(), List.of()));

    assertThat(outcome).isEqualTo(new TripOutcome(0, 0, 0, 0, null));
  }
}
