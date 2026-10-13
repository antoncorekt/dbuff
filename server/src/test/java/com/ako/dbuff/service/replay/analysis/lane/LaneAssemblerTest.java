package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inLane;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.jungleFarm;
import static org.assertj.core.api.Assertions.assertThat;

class LaneAssemblerTest {
  private static final long RADIANT = 0;
  private static final long DIRE = 128;

  private final CreepValues values = new CreepValues("replay/creep-values.json");

  private LaneRows assemble(LaneProperties properties) {
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT, List.of(inLane(0, 800, Zone.Lane.MID)),
            DIRE, List.of(inLane(0, 300, Zone.Lane.MID), jungleFarm(300, 800)));
    LaneInputs inputs =
        new LaneInputs(
            1L,
            Map.of(RADIANT, TeamSide.RADIANT, DIRE, TeamSide.DIRE),
            segments,
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of());
    return new LaneAssembler(properties, values, List.of()).assemble(inputs, 1);
  }

  private static LaneAssignmentDomain of(LaneRows rows, long slot) {
    return rows.assignments().stream()
        .filter(a -> a.getPlayerSlot() == slot)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void assemble_endsTheWindowOfTheSideThatStayedWhenTheOtherSideHasLeft() {
    LaneRows rows = assemble(new LaneProperties());

    assertThat(of(rows, RADIANT).getEndSeconds()).isEqualTo(300);
    assertThat(of(rows, RADIANT).getEndReason()).isEqualTo("OPPONENTS_LEFT");
    assertThat(of(rows, DIRE).getEndReason()).isEqualTo("JUNGLE");
    assertThat(rows.results())
        .allSatisfy(
            r -> {
              assertThat(r.getEndSeconds()).isEqualTo(300);
              assertThat(r.getEndReason()).isEqualTo("JUNGLE");
            });
  }

  @Test
  void assemble_letsTheSideStayUntilItsOwnEndWhenThePropertyIsOff() {
    LaneProperties properties = new LaneProperties();
    properties.setEndWhenOpponentsLeft(false);

    LaneRows rows = assemble(properties);

    assertThat(of(rows, RADIANT).getEndSeconds()).isEqualTo(800);
    assertThat(of(rows, RADIANT).getEndReason()).isEqualTo("GAME_END");
  }

  @Test
  void assemble_givesAHeroWithNoActivityNoLane() {
    LaneInputs inputs =
        new LaneInputs(
            1L,
            Map.of(RADIANT, TeamSide.RADIANT),
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of());

    LaneRows rows = new LaneAssembler(new LaneProperties(), values, List.of()).assemble(inputs, 1);

    assertThat(rows.assignments()).singleElement().satisfies(a -> assertThat(a.getLane()).isNull());
    assertThat(rows.results()).isEmpty();
  }
}
