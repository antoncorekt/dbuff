package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LaneProxyTest {
  private static final List<LaneSlot> LANES =
      List.of(
          new LaneSlot(0, TeamSide.RADIANT, Zone.Lane.BOTTOM, 0, 600),
          new LaneSlot(1, TeamSide.RADIANT, Zone.Lane.BOTTOM, 0, 600),
          new LaneSlot(128, TeamSide.DIRE, Zone.Lane.BOTTOM, 0, 600),
          new LaneSlot(2, TeamSide.RADIANT, Zone.Lane.MID, 0, 600));
  private final LaneProxy proxy = new LaneProxy(60);

  private static CreepHit hit(long slot, double seconds, Zone.Lane lane, boolean denied) {
    return new CreepHit(slot, seconds, lane, denied);
  }

  @Test
  void of_countsThePartnersAndTheEnemiesOfTheLaneInTheWindowAfterHeLeft() {
    List<CreepHit> creeps =
        List.of(
            hit(1, 310, Zone.Lane.BOTTOM, false),
            hit(128, 320, Zone.Lane.BOTTOM, false),
            hit(128, 330, Zone.Lane.BOTTOM, false),
            hit(128, 340, Zone.Lane.BOTTOM, true),
            hit(128, 400, Zone.Lane.BOTTOM, false),
            hit(128, 299, Zone.Lane.BOTTOM, false),
            hit(0, 315, Zone.Lane.BOTTOM, false),
            hit(128, 320, Zone.Lane.MID, false),
            hit(128, 320, null, false));

    Optional<LaneProxy.Result> result = proxy.of(0, 300, LANES, creeps);

    assertThat(result).contains(new LaneProxy.Result(1, 2));
    assertThat(result.get().lost()).isTrue();
  }

  @Test
  void of_aLaneWhoseEnemiesKilledNoMoreHeldItself() {
    List<CreepHit> creeps =
        List.of(hit(1, 310, Zone.Lane.BOTTOM, false), hit(128, 320, Zone.Lane.BOTTOM, false));

    assertThat(proxy.of(0, 300, LANES, creeps).get().lost()).isFalse();
  }

  @Test
  void of_aHeroWithNoLaneOrOneWhoseLaningEndedHasNoProxy() {
    assertThat(proxy.of(5, 300, LANES, List.of())).isEmpty();
    assertThat(proxy.of(0, 700, LANES, List.of())).isEmpty();
  }

  @Test
  void of_aKillerWithNoLaneAssignmentCountsBySlotTeam() {
    List<CreepHit> creeps =
        List.of(hit(3, 310, Zone.Lane.BOTTOM, false), hit(131, 320, Zone.Lane.BOTTOM, false));

    assertThat(proxy.of(0, 300, LANES, creeps)).contains(new LaneProxy.Result(1, 1));
  }
}
