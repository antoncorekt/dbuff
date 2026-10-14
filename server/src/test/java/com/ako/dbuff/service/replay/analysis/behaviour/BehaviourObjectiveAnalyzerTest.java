package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.analysis.fight.PlayerTraces;
import com.ako.dbuff.service.replay.map.MapLayouts;
import com.ako.dbuff.service.replay.map.MatchMapLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
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
  BehaviourObjectiveAnalyzer.class,
  BehaviourProperties.class,
  PlayerTraces.class,
  SlotTraces.class,
  CombatLogReader.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  BehaviourObjectiveAnalyzerTest.Json.class
})
class BehaviourObjectiveAnalyzerTest {
  private static final long MATCH = 15L;
  private static final int SAMPLES = 40;
  private static final int HERO = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  @Autowired private BehaviourObjectiveAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private GameStateSegmentRepo stateRepo;
  @Autowired private ObjectiveConversionRepo conversionRepo;

  @Test
  void analyze_aTowerOfTheLoserAfterAnImportantFightIsConverted() {
    stored(layouts.frozenVersion(), 300, false);
    buildingRepo.saveAndFlush(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .buildingName("npc_dota_badguys_tower1_mid")
            .kind("TOWER")
            .ownerTeam("DIRE")
            .seconds(130.0)
            .denied(false)
            .build());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<ObjectiveConversionDomain> rows = conversionRepo.findAll();
    assertThat(rows).hasSize(1);
    ObjectiveConversionDomain row = rows.getFirst();
    assertThat(row.getFightIndex()).isEqualTo(1);
    assertThat(row.getWinnerTeam()).isEqualTo("RADIANT");
    assertThat(row.getResult()).isEqualTo("CONVERTED");
    assertThat(row.getObjectiveKind()).isEqualTo("TOWER");
    assertThat(row.getObjectiveKey()).isEqualTo("npc_dota_badguys_tower1_mid");
    assertThat(row.getSecondsAfter()).isEqualTo(30);
    assertThat(row.getStateBefore()).isEqualTo("BEHIND");
    assertThat(row.getAnalysisVersion()).isEqualTo(1);
  }

  @Test
  void analyze_roshanKilledByTheWinnerIsConvertedWithTheCall() {
    stored(layouts.frozenVersion(), 300, true);

    analyzer.analyze(MATCH);

    ObjectiveConversionDomain row = conversionRepo.findAll().getFirst();
    assertThat(row.getObjectiveKind()).isEqualTo("ROSHAN");
    assertThat(row.getRoshanNearSlots()).isEqualTo("0,1");
    assertThat(row.getRoshanCall()).isEqualTo("GAMBLE");
  }

  @Test
  void analyze_nothingTakenIsMissedWithTheNearestStandingTower() {
    stored(layouts.frozenVersion(), 300, false);

    analyzer.analyze(MATCH);

    ObjectiveConversionDomain row = conversionRepo.findAll().getFirst();
    assertThat(row.getResult()).isEqualTo("MISSED");
    assertThat(row.getObjectiveKind()).isNull();
    assertThat(row.getNearestTower()).startsWith("dota_badguys_tower");
    assertThat(row.getTowerDistance()).isPositive();
    assertThat(row.getWinnersAlive()).isEqualTo(2);
  }

  @Test
  void analyze_aFightWhoseWindowRunsPastTheEndOfTheTracksStoresNothing() {
    stored(layouts.frozenVersion(), 150, false, 30);

    analyzer.analyze(MATCH);

    assertThat(conversionRepo.findAll()).isEmpty();
  }

  @Test
  void analyze_noImportantFightReplacesEarlierRowsWithNone() {
    stored(layouts.frozenVersion(), 300, false);
    importanceRepo.deleteAll();
    conversionRepo.saveAndFlush(ObjectiveConversionDomain.builder().matchId(MATCH).build());

    analyzer.analyze(MATCH);

    assertThat(conversionRepo.findAll()).isEmpty();
  }

  @Test
  void analyze_keepsEarlierRowsWhenTheNewOnesCannotBeBuilt() {
    stored("0123456789ab", 300, false);
    conversionRepo.saveAndFlush(ObjectiveConversionDomain.builder().matchId(MATCH).build());

    analyzer.analyze(MATCH);

    assertThat(conversionRepo.findAll()).hasSize(1);
  }

  @Test
  void deleteByMatchId_removesTheConversions() {
    stored(layouts.frozenVersion(), 300, false);
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(conversionRepo.findAll()).isEmpty();
  }

  private static CombatEvent event(
      CombatEventKind kind, double second, String attacker, String target, int flags) {
    return new CombatEvent(kind, second, attacker, target, "", flags, 0, 0, 0, 4500, 0, 0, 1 << 1);
  }

  private void track(long slot, int samples) {
    int[] xs = new int[samples];
    Arrays.fill(xs, -1000);
    int[] ones = new int[samples];
    Arrays.fill(ones, 1);
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(samples)
            .x(xs)
            .y(xs)
            .hpPercent(ones)
            .manaPercent(ones)
            .level(ones)
            .flags(ones)
            .build());
  }

  private void player(long slot, int index) {
    playerRepo.saveAndFlush(
        ReplayPlayerDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .replayPlayerId(index * 2)
            .heroEntityName("Hero" + index)
            .build());
  }

  private void stored(String mapVersion, int lastEventSecond, boolean roshan) {
    stored(mapVersion, lastEventSecond, roshan, SAMPLES);
  }

  private void stored(String mapVersion, int lastEventSecond, boolean roshan, int samples) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(processing.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .mapVersion(mapVersion)
            .build());
    track(0, samples);
    track(1, samples);
    player(0, 0);
    player(1, 1);
    player(128, 5);
    fightRepo.saveAndFlush(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .startSeconds(80.0)
            .endSeconds(100.0)
            .winner("RADIANT")
            .build());
    importanceRepo.saveAndFlush(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .winningTeam("RADIANT")
            .important(true)
            .build());
    stateRepo.saveAndFlush(
        GameStateSegmentDomain.builder()
            .matchId(MATCH)
            .team("RADIANT")
            .startSeconds(0)
            .endSeconds(300)
            .state("BEHIND")
            .build());
    List<CombatEvent> events =
        roshan
            ? List.of(
                event(
                    CombatEventKind.DEATH,
                    120,
                    "npc_dota_hero_hero0",
                    "npc_dota_roshan",
                    CombatEvent.ATTACKER_HERO),
                event(
                    CombatEventKind.DEATH,
                    lastEventSecond,
                    "npc_dota_hero_hero0",
                    "npc_dota_hero_hero5",
                    HERO))
            : List.of(
                event(
                    CombatEventKind.DEATH,
                    lastEventSecond,
                    "npc_dota_hero_hero0",
                    "npc_dota_hero_hero5",
                    HERO));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
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
