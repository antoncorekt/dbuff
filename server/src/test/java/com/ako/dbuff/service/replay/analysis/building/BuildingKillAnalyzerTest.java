package com.ako.dbuff.service.replay.analysis.building;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ObjectiveEventDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ObjectiveEventRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  BuildingKillAnalyzer.class,
  CombatLogReader.class,
  PlayerTraces.class,
  MatchMapLayout.class,
  MapLayouts.class,
  ReplayProcessingProperties.class,
  BuildingKillAnalyzerTest.Json.class
})
class BuildingKillAnalyzerTest {

  private static final long MATCH = 15L;

  @Autowired private BuildingKillAnalyzer analyzer;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private ObjectiveEventRepo objectiveRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private BuildingKillContextRepo contextRepo;

  @Test
  void analyze_describesEachTowerAndAncientAndSkipsFillers_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<BuildingKillContextDomain> rows = contextRepo.findByMatchId(MATCH);
    assertThat(rows).hasSize(2);
    BuildingKillContextDomain tower =
        rows.stream().filter(r -> r.getKind().equals("TOWER")).findFirst().orElseThrow();
    assertThat(tower.getOwnerTeam()).isEqualTo("RADIANT");
    assertThat(tower.getTier()).isEqualTo(1);
    assertThat(tower.getLane()).isEqualTo("BOTTOM");
    assertThat(tower.getKillerSlot()).isEqualTo(129L);
    assertThat(tower.getDenied()).isFalse();
    assertThat(tower.getFightIndex()).isZero();
    assertThat(tower.getAttackersNear()).as("no map layout was stored").isNull();
    BuildingKillContextDomain fort =
        rows.stream().filter(r -> r.getKind().equals("FORT")).findFirst().orElseThrow();
    assertThat(fort.getKillerSlot()).as("creeps took it").isNull();
    assertThat(fort.getFightIndex()).isNull();
  }

  @Test
  void attackersNear_countsOnlyAttackingHeroesAliveWithinRangeInTheLastTenSeconds() {
    Map<Integer, FightAssessor.Trace> traces =
        Map.of(
            5, trace(100, 0, 1),
            6, trace(5000, 0, 1),
            7, trace(100, 0, 0),
            0, trace(100, 0, 1));

    var near =
        BuildingKillAnalyzer.attackersNear(
            traces,
            BuildingName.parse("npc_dota_goodguys_tower1_bot").orElseThrow(),
            new double[] {0, 0},
            60);

    assertThat(near).containsExactly(128L);
  }

  @Test
  void deleteByMatchId_removesTheContext() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(contextRepo.findAll()).isEmpty();
  }

  private static FightAssessor.Trace trace(int x, int y, int alive) {
    int[] xs = new int[40];
    int[] ys = new int[40];
    int[] levels = new int[40];
    int[] flags = new int[40];
    Arrays.fill(xs, x);
    Arrays.fill(ys, y);
    Arrays.fill(levels, 10);
    Arrays.fill(flags, alive);
    return new FightAssessor.Trace(0, 5, xs, ys, levels, flags);
  }

  private void stored() {
    List<CombatEvent> events =
        List.of(
            building(588, "npc_dota_goodguys_tower1_bot"),
            building(600, "npc_dota_goodguys_fillers"),
            building(1641, "npc_dota_goodguys_fort"));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
    objectiveRepo.saveAndFlush(
        ObjectiveEventDomain.builder()
            .matchId(MATCH)
            .objectiveIndex(0)
            .eventTime(588L)
            .eventType("building_kill")
            .unit("npc_dota_hero_lycan")
            .objectiveKey("npc_dota_goodguys_tower1_bot")
            .playerSlot(129L)
            .build());
    objectiveRepo.saveAndFlush(
        ObjectiveEventDomain.builder()
            .matchId(MATCH)
            .objectiveIndex(1)
            .eventTime(1641L)
            .eventType("building_kill")
            .unit("npc_dota_creep_badguys_melee_upgraded_mega")
            .objectiveKey("npc_dota_goodguys_fort")
            .build());
    fightRepo.saveAndFlush(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .type("SKIRMISH")
            .startSeconds(570.0)
            .endSeconds(590.0)
            .build());
  }

  private static CombatEvent building(double second, String name) {
    return new CombatEvent(
        CombatEventKind.BUILDING_KILL, second, "", name, "", 0, 1, 0, 0, 0, 0, 0, 0);
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
