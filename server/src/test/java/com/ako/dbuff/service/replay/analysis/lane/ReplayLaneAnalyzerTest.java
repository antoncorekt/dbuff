package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LanePlayerStatRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  ReplayLaneAnalyzer.class,
  LaneProperties.class,
  CreepValues.class,
  StoredActivity.class,
  CombatLogReader.class,
  ReportHeroNames.class,
  PartnerAbsentContributor.class,
  EnemyGankContributor.class,
  HarassmentContributor.class,
  MidRuneContributor.class,
  SupportPresenceContributor.class,
  StackContributor.class,
  MatchupContributor.class,
  CreepEdgeContributor.class,
  EarlyKillContributor.class,
  UltimateContributor.class,
  ReplayLaneAnalyzerTest.Beans.class
})
class ReplayLaneAnalyzerTest {
  private static final long MATCH = 7L;
  private static final long RADIANT_MID = 2L;
  private static final long DIRE_MID = 130L;
  private static final long RADIANT_BOTTOM = 0L;
  private static final long RADIANT_ROAMER = 4L;

  @Autowired private ReplayLaneAnalyzer analyzer;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private PlayerActivitySegmentRepo segmentRepo;
  @Autowired private CreepKillRepo creepRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private LaneAssignmentRepo assignmentRepo;
  @Autowired private LanePlayerStatRepo statRepo;
  @Autowired private LaneResultRepo resultRepo;
  @Autowired private LaneReasonRepo reasonRepo;

  @Test
  void analyze_storesLanesWindowsResultsAndReasonsForTheStoredActivity_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<LaneAssignmentDomain> assignments = assignmentRepo.findByMatchId(MATCH);
    assertThat(assignments).hasSize(4);
    LaneAssignmentDomain radiantMid = assignment(assignments, RADIANT_MID);
    assertThat(radiantMid.getLane()).isEqualTo("MID");
    assertThat(radiantMid.getPairId()).isEqualTo(2);
    assertThat(radiantMid.getPosition()).isEqualTo("MID");
    assertThat(radiantMid.getStartSeconds()).isEqualTo(10);
    assertThat(radiantMid.getEndSeconds()).as("he stayed, the opponent left").isEqualTo(420);
    assertThat(radiantMid.getEndReason()).isEqualTo("OPPONENTS_LEFT");
    LaneAssignmentDomain direMid = assignment(assignments, DIRE_MID);
    assertThat(direMid.getEndSeconds()).isEqualTo(420);
    assertThat(direMid.getEndReason()).isEqualTo("JUNGLE");
    assertThat(assignment(assignments, RADIANT_BOTTOM).getPosition()).isEqualTo("SAFE");
    LaneAssignmentDomain roamer = assignment(assignments, RADIANT_ROAMER);
    assertThat(roamer.getLane()).isNull();
    assertThat(roamer.getPairId()).isNull();

    List<LaneResultDomain> results = resultRepo.findByMatchId(MATCH);
    assertThat(results).hasSize(3);
    LaneResultDomain radiant = result(results, 2, "RADIANT");
    LaneResultDomain dire = result(results, 2, "DIRE");
    assertThat(radiant.getResult()).isEqualTo("WON");
    assertThat(dire.getResult()).isEqualTo("LOST");
    assertThat(radiant.getMargin()).isPositive().isEqualTo(-dire.getMargin(), offset(1e-9));
    assertThat(radiant.getLastHits()).isEqualTo(8);
    assertThat(radiant.getSiegeLastHits()).isEqualTo(1);
    assertThat(dire.getLastHits()).isEqualTo(3);
    assertThat(dire.getSideEndReason()).isEqualTo("JUNGLE");
    assertThat(dire.getEndReason())
        .as("the pair ends when the last hero leaves")
        .isEqualTo("JUNGLE");
    assertThat(result(results, 1, "RADIANT").getResult()).isEqualTo("UNCONTESTED");

    assertThat(statRepo.findByMatchId(MATCH)).hasSize(3);
    assertThat(statRepo.findByMatchId(MATCH))
        .filteredOn(s -> s.getPlayerSlot() == RADIANT_MID)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getSideShare()).isEqualTo(1.0);
              assertThat(s.getCreepScore()).isPositive();
            });
    assertThat(reasonRepo.findByMatchId(MATCH))
        .filteredOn(r -> r.getPairId() == 2 && r.getTeam().equals("DIRE"))
        .extracting(r -> r.getCode())
        .contains("SIEGE_EDGE");
    assertThat(reasonRepo.findByMatchId(MATCH))
        .filteredOn(r -> r.getPairId() == 1)
        .extracting(r -> r.getCode())
        .contains("PARTNER_ROAMING");
  }

  @Test
  void analyze_endsAllLaningAtTheFirstLaneTowerAndMarksTheOwnersTowerOnTheResult() {
    stored();
    buildingRepo.saveAndFlush(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .buildingName("npc_dota_badguys_tower1_mid")
            .seconds(300.4)
            .kind("TOWER")
            .tier(1)
            .lane("MID")
            .ownerTeam("DIRE")
            .build());

    analyzer.analyze(MATCH);

    List<LaneResultDomain> results = resultRepo.findByMatchId(MATCH);
    LaneResultDomain dire = result(results, 2, "DIRE");
    LaneResultDomain radiant = result(results, 2, "RADIANT");
    assertThat(dire.getEndSeconds()).isEqualTo(300);
    assertThat(dire.getEndReason()).isEqualTo("TOWER_FELL");
    assertThat(dire.getOwnTowerFell()).isTrue();
    assertThat(radiant.getEnemyTowerFell()).isTrue();
    assertThat(radiant.getResult()).isEqualTo("WON");
  }

  @Test
  void analyze_skipsTracksAndRolesWithoutASlotOrARole() {
    stored();
    roleRepo.save(PlayerRoleDomain.builder().matchId(MATCH).playerSlot(RADIANT_MID).build());
    trackRepo.save(ReplayPlayerTrackDomain.builder().matchId(MATCH).build());

    analyzer.analyze(MATCH);

    assertThat(resultRepo.findByMatchId(MATCH)).hasSize(3);
  }

  @Test
  void analyze_storesNothingWithoutStoredActivity() {
    analyzer.analyze(MATCH);

    assertThat(assignmentRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(resultRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void deleteByMatchId_removesEveryLaneTable() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(assignmentRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(statRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(resultRepo.findByMatchId(MATCH)).isEmpty();
    assertThat(reasonRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void requires_namesTheAnalyzersAndExtractorsItReads() {
    assertThat(analyzer.requires())
        .contains("activity", "creep-kills", "runes", "roles", "deaths", "building-kills");
    assertThat(analyzer.name()).isEqualTo("lanes");
  }

  private void stored() {
    for (long slot : new long[] {RADIANT_BOTTOM, RADIANT_MID, RADIANT_ROAMER, DIRE_MID}) {
      playerRepo.save(
          ReplayPlayerDomain.builder()
              .matchId(MATCH)
              .playerSlot(slot)
              .teamNum(slot >= 128 ? 3 : 2)
              .build());
    }
    roleRepo.save(
        PlayerRoleDomain.builder().matchId(MATCH).playerSlot(RADIANT_ROAMER).role(5).build());
    segment(RADIANT_BOTTOM, 0, 600, "LANE_FARM", "LANE", "BOTTOM");
    segment(RADIANT_MID, 10, 600, "LANE_FARM", "LANE", "MID");
    segment(RADIANT_ROAMER, 0, 600, "JUNGLE_FARM", "JUNGLE", null);
    segment(DIRE_MID, 10, 420, "LANE_FARM", "LANE", "MID");
    segment(DIRE_MID, 420, 600, "JUNGLE_FARM", "JUNGLE", null);
    for (int i = 0; i < 7; i++) {
      creep(RADIANT_MID, "LANE_MELEE", 60 + i * 20.0, false);
    }
    creep(RADIANT_MID, "LANE_SIEGE", 300, false);
    for (int i = 0; i < 3; i++) {
      creep(DIRE_MID, "LANE_MELEE", 70 + i * 20.0, false);
    }
    creep(DIRE_MID, "LANE_MELEE", 200, true);
    creepRepo.flush();
  }

  private void segment(long slot, int from, int to, String activity, String zone, String lane) {
    segmentRepo.saveAndFlush(
        PlayerActivitySegmentDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(from)
            .endSeconds(to)
            .activity(activity)
            .zone(zone)
            .lane(lane)
            .build());
  }

  private void creep(long slot, String creepClass, double seconds, boolean denied) {
    creepRepo.save(
        CreepKillDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .seconds(seconds)
            .creepClass(creepClass)
            .denied(denied)
            .build());
  }

  private static LaneAssignmentDomain assignment(List<LaneAssignmentDomain> rows, long slot) {
    return rows.stream().filter(r -> r.getPlayerSlot() == slot).findFirst().orElseThrow();
  }

  private static LaneResultDomain result(List<LaneResultDomain> rows, int pair, String team) {
    return rows.stream()
        .filter(r -> r.getPairId() == pair && r.getTeam().equals(team))
        .findFirst()
        .orElseThrow();
  }

  @TestConfiguration
  static class Beans {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    HeroKits heroKits() {
      return new HeroKits() {
        @Override
        public Optional<HeroConstant> hero(long heroId) {
          return Optional.empty();
        }

        @Override
        public Optional<AbilityConstant> ability(String name) {
          return Optional.empty();
        }
      };
    }
  }
}
