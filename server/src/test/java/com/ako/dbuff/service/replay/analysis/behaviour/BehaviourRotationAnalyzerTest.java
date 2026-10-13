package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
  BehaviourRotationAnalyzer.class,
  BehaviourProperties.class,
  PlayerTraces.class,
  StoredActivity.class,
  CombatLogReader.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  BehaviourRotationAnalyzerTest.Json.class
})
class BehaviourRotationAnalyzerTest {
  private static final long MATCH = 12L;
  private static final int SAMPLES = 40;

  @Autowired private BehaviourRotationAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private TeamRotationRepo rotationRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;

  @Test
  void analyze_storesTheRotationOfAHeroWhoLeftTheGroupAndTookPartInAWonFight() {
    stored(layouts.frozenVersion());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<TeamRotationDomain> rows = rotationRepo.findAll();
    assertThat(rows).hasSize(1);
    TeamRotationDomain row = rows.getFirst();
    assertThat(row.getPlayerSlot()).isEqualTo(4L);
    assertThat(row.getTeam()).isEqualTo("RADIANT");
    assertThat(row.getToZone()).isEqualTo("LANE");
    assertThat(row.getFights()).isEqualTo(1);
    assertThat(row.getFightsWon()).isEqualTo(1);
    assertThat(row.getVerdict()).isEqualTo("GOOD");
    assertThat(row.getReasons()).isEmpty();
    assertThat(row.getAnalysisVersion()).isEqualTo(2);
  }

  @Test
  void analyze_storesAnOwnTowerLostWhileAwayAsAReason() {
    stored(layouts.frozenVersion());
    buildingRepo.saveAndFlush(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .kind("TOWER")
            .ownerTeam("RADIANT")
            .seconds(50.0)
            .build());

    analyzer.analyze(MATCH);

    assertThat(rotationRepo.findAll().getFirst().getReasons()).isEqualTo("TOWER_LOST_WHILE_AWAY");
  }

  @Test
  void hits_countOnlyDamageToEnemyHeroesAndEnemyTowers() {
    Map<String, Long> slots =
        Map.of("npc_dota_hero_axe", 0L, "npc_dota_hero_lion", 1L, "npc_dota_hero_mirana", 128L);
    int hero = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
    List<CombatEvent> events =
        List.of(
            damage("npc_dota_hero_axe", "npc_dota_hero_mirana", hero, 10),
            damage("npc_dota_hero_axe", "npc_dota_hero_lion", hero, 11),
            damage("npc_dota_hero_axe", "npc_dota_hero_axe", hero, 12),
            damage(
                "npc_dota_hero_axe", "npc_dota_badguys_tower1_top", CombatEvent.ATTACKER_HERO, 13),
            damage(
                "npc_dota_hero_axe", "npc_dota_goodguys_tower1_top", CombatEvent.ATTACKER_HERO, 14),
            damage(
                "npc_dota_hero_axe",
                "npc_dota_creep_badguys_melee",
                CombatEvent.ATTACKER_HERO,
                15));

    assertThat(BehaviourRotationAnalyzer.hits(events, slots))
        .containsExactly(new DamageHit(0L, 10, false), new DamageHit(0L, 13, true));
  }

  private static CombatEvent damage(String attacker, String target, int flags, double second) {
    return new CombatEvent(
        CombatEventKind.DAMAGE, second, attacker, target, "", flags, 50, 0, 0, 0, 0, 0, 0);
  }

  @Test
  void analyze_storesNothingForAMatchPlayedOnAnotherMapVersion() {
    stored("0123456789ab");

    analyzer.analyze(MATCH);

    assertThat(rotationRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheRotations() {
    stored(layouts.frozenVersion());
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(rotationRepo.findAll()).isEmpty();
  }

  private void track(long slot, int[] x, int[] y) {
    int[] ones = new int[SAMPLES];
    Arrays.fill(ones, 1);
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(SAMPLES)
            .x(x)
            .y(y)
            .hpPercent(ones)
            .manaPercent(ones)
            .level(ones)
            .flags(ones)
            .build());
  }

  private void stored(String mapVersion) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(processing.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .mapVersion(mapVersion)
            .build());
    int[] mid = IntStream.range(0, SAMPLES).map(i -> -1000).toArray();
    for (long slot = 0; slot < 4; slot++) {
      track(slot, mid, mid);
    }
    track(
        4,
        IntStream.range(0, SAMPLES).map(i -> i < 4 ? -1000 : -6000).toArray(),
        IntStream.range(0, SAMPLES).map(i -> i < 4 ? -1000 : 5500).toArray());
    fightRepo.saveAndFlush(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .startSeconds(60.0)
            .endSeconds(80.0)
            .winner("RADIANT")
            .build());
    fightPlayerRepo.saveAndFlush(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(4L)
            .presence("ACTIVE")
            .build());
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(0)
            .data(CombatLogCodec.encode(List.of()))
            .build());
  }

  @TestConfiguration
  static class Json {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
