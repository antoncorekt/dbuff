package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inLane;
import static com.ako.dbuff.service.replay.analysis.lane.LaneTestData.inRiver;
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

  private static LaneInputs inputs(
      Map<Long, List<ActivitySegment>> segments,
      Map<Long, TeamSide> teams,
      List<BuildingKillContextDomain> buildings,
      List<CreepKillDomain> creeps,
      Map<Long, ReplayPlayerTrackDomain> tracks) {
    return new LaneInputs(
        1L, teams, segments, buildings, creeps, tracks, Map.of(), Map.of(), Map.of(), List.of(),
        Map.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
  }

  @Test
  void assemble_givesASupportWhoShadowedTheCarryHisLaneAndMakesThemPartners() {
    long support = 1;
    long enemy = DIRE;
    ReplayPlayerTrackDomain beside =
        ReplayPlayerTrackDomain.builder()
            .startSeconds(0)
            .stepSeconds(10)
            .x(new int[80])
            .y(new int[80])
            .build();
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT, List.of(inLane(0, 600, Zone.Lane.MID)),
            support, List.of(inLane(0, 100, Zone.Lane.MID), inRiver(100, 600)),
            enemy, List.of(inLane(0, 600, Zone.Lane.MID)));

    LaneRows rows =
        new LaneAssembler(new LaneProperties(), values, List.of())
            .assemble(
                inputs(
                    segments,
                    Map.of(
                        RADIANT, TeamSide.RADIANT, support, TeamSide.RADIANT, enemy, TeamSide.DIRE),
                    List.of(),
                    List.of(),
                    Map.of(RADIANT, beside, support, beside)),
                1);

    assertThat(of(rows, support).getLane()).isEqualTo("MID");
    assertThat(of(rows, support).getPairId()).isEqualTo(of(rows, RADIANT).getPairId());
  }

  @Test
  void assemble_endsTheWindowOfAShadowingHeroWhereTheHoldersWindowWasClipped() {
    long support = 1;
    LaneProperties properties = new LaneProperties();
    properties.setMinLaneShare(0.99);
    ReplayPlayerTrackDomain beside =
        ReplayPlayerTrackDomain.builder()
            .startSeconds(0)
            .stepSeconds(10)
            .x(new int[80])
            .y(new int[80])
            .build();
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT, List.of(inLane(0, 800, Zone.Lane.MID)),
            support, List.of(inLane(0, 800, Zone.Lane.MID)),
            DIRE, List.of(inLane(0, 300, Zone.Lane.MID), jungleFarm(300, 800)));
    Map<Long, TeamSide> teams =
        Map.of(RADIANT, TeamSide.RADIANT, support, TeamSide.RADIANT, DIRE, TeamSide.DIRE);

    LaneRows rows =
        new LaneAssembler(properties, values, List.of())
            .assemble(
                inputs(
                    segments,
                    teams,
                    List.of(),
                    List.of(),
                    Map.of(RADIANT, beside, support, beside)),
                1);

    assertThat(of(rows, RADIANT).getEndSeconds()).isEqualTo(300);
    assertThat(of(rows, support).getLane()).isEqualTo("MID");
    assertThat(of(rows, support).getEndSeconds()).isEqualTo(300);
  }

  @Test
  void assemble_listsAHeroWhoArrivesAfterTheOtherSideLeftAsHavingNoLaneAndKeepsHimOutOfTheRows() {
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT,
            List.of(inLane(0, 800, Zone.Lane.MID)),
            DIRE,
            List.of(inLane(0, 300, Zone.Lane.MID), jungleFarm(300, 800)),
            RADIANT + 1,
            List.of(
                inLane(0, 40, Zone.Lane.TOP),
                jungleFarm(40, 400),
                inLane(400, 800, Zone.Lane.MID)));
    LaneInputs in =
        inputs(
            segments,
            Map.of(RADIANT, TeamSide.RADIANT, RADIANT + 1, TeamSide.RADIANT, DIRE, TeamSide.DIRE),
            List.of(),
            List.of(),
            Map.of());

    LaneRows rows = new LaneAssembler(new LaneProperties(), values, List.of()).assemble(in, 1);

    assertThat(of(rows, RADIANT + 1).getLane()).isNull();
    assertThat(rows.playerStats()).extracting(s -> s.getPlayerSlot()).doesNotContain(RADIANT + 1);
    assertThat(rows.results()).allSatisfy(r -> assertThat(r.getHeroes()).isEqualTo(1));
  }

  @Test
  void assemble_listsAHeroWhoArrivesAfterTheLaneTowerFellAsHavingNoLane() {
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT,
            List.of(inLane(0, 800, Zone.Lane.MID)),
            DIRE,
            List.of(inLane(0, 800, Zone.Lane.MID)),
            RADIANT + 1,
            List.of(inRiver(0, 100), inLane(100, 800, Zone.Lane.MID)));
    BuildingKillContextDomain tower =
        BuildingKillContextDomain.builder()
            .seconds(60.0)
            .kind("TOWER")
            .tier(1)
            .lane("MID")
            .ownerTeam("DIRE")
            .build();

    LaneRows rows =
        new LaneAssembler(new LaneProperties(), values, List.of())
            .assemble(
                inputs(
                    segments,
                    Map.of(
                        RADIANT,
                        TeamSide.RADIANT,
                        RADIANT + 1,
                        TeamSide.RADIANT,
                        DIRE,
                        TeamSide.DIRE),
                    List.of(tower),
                    List.of(),
                    Map.of()),
                1);

    assertThat(of(rows, RADIANT + 1).getLane()).as("arrived after the tower fell").isNull();
    assertThat(rows.results())
        .allSatisfy(r -> assertThat(r.getEndReason()).isEqualTo("TOWER_FELL"));
  }

  @Test
  void assemble_skipsRowsWithMissingValuesInsteadOfFailing() {
    ReplayPlayerTrackDomain noStep =
        ReplayPlayerTrackDomain.builder().playerSlot(RADIANT).level(new int[] {1, 2, 3}).build();
    CreepKillDomain noSlot =
        CreepKillDomain.builder().creepClass("LANE_MELEE").seconds(50.0).denied(false).build();
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT, List.of(inLane(0, 800, Zone.Lane.MID)),
            DIRE, List.of(inLane(0, 800, Zone.Lane.MID)));

    LaneRows rows =
        new LaneAssembler(new LaneProperties(), values, List.of())
            .assemble(
                inputs(
                    segments,
                    Map.of(RADIANT, TeamSide.RADIANT, DIRE, TeamSide.DIRE),
                    List.of(),
                    List.of(noSlot),
                    Map.of(RADIANT, noStep)),
                1);

    assertThat(rows.playerStats())
        .hasSize(2)
        .allSatisfy(
            s -> {
              assertThat(s.getLevel()).isNull();
              assertThat(s.getLastHits()).isZero();
            });
  }

  @Test
  void assemble_storesTheSecondAHeroReachedTheUltimateLevelOrNullWhenHeDidNot() {
    ReplayPlayerTrackDomain early =
        ReplayPlayerTrackDomain.builder()
            .playerSlot(RADIANT)
            .startSeconds(0)
            .stepSeconds(100)
            .level(new int[] {1, 3, 5, 6, 6, 7, 7, 8, 8})
            .build();
    ReplayPlayerTrackDomain late =
        ReplayPlayerTrackDomain.builder()
            .playerSlot(DIRE)
            .startSeconds(0)
            .stepSeconds(100)
            .level(new int[] {1, 3, 5, 5, 5, 5, 5, 6, 6})
            .build();
    Map<Long, List<ActivitySegment>> segments =
        Map.of(
            RADIANT, List.of(inLane(0, 600, Zone.Lane.MID)),
            DIRE, List.of(inLane(0, 600, Zone.Lane.MID)));

    LaneRows rows =
        new LaneAssembler(new LaneProperties(), values, List.of())
            .assemble(
                inputs(
                    segments,
                    Map.of(RADIANT, TeamSide.RADIANT, DIRE, TeamSide.DIRE),
                    List.of(),
                    List.of(),
                    Map.of(RADIANT, early, DIRE, late)),
                1);

    assertThat(rows.playerStats())
        .filteredOn(s -> s.getPlayerSlot() == RADIANT)
        .singleElement()
        .satisfies(s -> assertThat(s.getUltimateSeconds()).isEqualTo(300));
    assertThat(rows.playerStats())
        .filteredOn(s -> s.getPlayerSlot() == DIRE)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getLevel()).isEqualTo(5);
              assertThat(s.getUltimateSeconds()).as("level 6 came at 700, after 600").isNull();
            });
  }
}
