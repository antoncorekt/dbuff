package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
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
import java.util.ArrayList;
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
  BehaviourSmokeAnalyzer.class,
  BehaviourProperties.class,
  PlayerTraces.class,
  SlotTraces.class,
  StoredEventTimes.class,
  StoredActivity.class,
  CombatLogReader.class,
  MapLayouts.class,
  MatchMapLayout.class,
  ReplayProcessingProperties.class,
  BehaviourSmokeAnalyzerTest.Json.class
})
class BehaviourSmokeAnalyzerTest {
  private static final long MATCH = 14L;
  private static final int SAMPLES = 40;
  private static final int HERO = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  @Autowired private BehaviourSmokeAnalyzer analyzer;
  @Autowired private MapLayouts layouts;
  @Autowired private ReplayProcessingProperties processing;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;
  @Autowired private SmokeUseRepo smokeRepo;

  @Test
  void analyze_storesTheSmokeWithItsGroupEndReasonAndOutcome() {
    stored(layouts.frozenVersion(), events(true));
    buildingRepo.saveAndFlush(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .kind("BARRACKS_MELEE")
            .ownerTeam("DIRE")
            .seconds(150.0)
            .denied(false)
            .killerSlot(1L)
            .build());

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<SmokeUseDomain> rows = smokeRepo.findAll();
    assertThat(rows).hasSize(1);
    SmokeUseDomain row = rows.getFirst();
    assertThat(row.getTeam()).isEqualTo("RADIANT");
    assertThat(row.getUserSlot()).isZero();
    assertThat(row.getGroupSlots()).isEqualTo("0,1");
    assertThat(row.getStartSeconds()).isEqualTo(100);
    assertThat(row.getEndSeconds()).isEqualTo(130);
    assertThat(row.getEndReason()).isEqualTo("BROKEN");
    assertThat(row.getStartZone()).isEqualTo("LANE");
    assertThat(row.getOutcome()).isEqualTo("PAID_OFF");
    assertThat(row.getKills()).isEqualTo(1);
    assertThat(row.getBuildings()).isEqualTo(1);
    assertThat(row.getAnalysisVersion()).isEqualTo(1);
  }

  @Test
  void analyze_keepsEarlierRowsWhenTheNewOnesCannotBeBuilt() {
    stored("0123456789ab", events(true));
    smokeRepo.saveAndFlush(SmokeUseDomain.builder().matchId(MATCH).team("DIRE").build());

    analyzer.analyze(MATCH);

    assertThat(smokeRepo.findAll()).hasSize(1);
  }

  @Test
  void analyze_aUseWithNoModifierStoresNothing() {
    stored(layouts.frozenVersion(), events(false));

    analyzer.analyze(MATCH);

    assertThat(smokeRepo.findAll()).isEmpty();
  }

  @Test
  void analyze_storesNothingForAMatchPlayedOnAnotherMapVersion() {
    stored("0123456789ab", events(true));

    analyzer.analyze(MATCH);

    assertThat(smokeRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheSmokeUses() {
    stored(layouts.frozenVersion(), events(true));
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(smokeRepo.findAll()).isEmpty();
  }

  private static CombatEvent event(
      CombatEventKind kind, double second, String attacker, String target, String inflictor) {
    return new CombatEvent(
        kind, second, attacker, target, inflictor, HERO, 0, 0, 0, 4500, 0, 0, 1 << 1);
  }

  private static List<CombatEvent> events(boolean withModifiers) {
    List<CombatEvent> events = new ArrayList<>();
    events.add(event(CombatEventKind.ITEM, 100, "npc_dota_hero_hero0", "", SmokeEvents.ITEM));
    if (withModifiers) {
      for (String hero : List.of("npc_dota_hero_hero0", "npc_dota_hero_hero1")) {
        events.add(
            event(
                CombatEventKind.MODIFIER_ADD,
                100,
                "npc_dota_hero_hero0",
                hero,
                SmokeEvents.MODIFIER));
      }
      events.add(
          event(
              CombatEventKind.MODIFIER_REMOVE,
              130,
              "npc_dota_hero_hero0",
              "npc_dota_hero_hero0",
              SmokeEvents.MODIFIER));
      events.add(
          event(
              CombatEventKind.MODIFIER_REMOVE,
              145,
              "npc_dota_hero_hero0",
              "npc_dota_hero_hero1",
              SmokeEvents.MODIFIER));
    }
    events.add(event(CombatEventKind.DEATH, 140, "npc_dota_hero_hero0", "npc_dota_hero_hero5", ""));
    return events;
  }

  private void track(long slot, int x) {
    int[] xs = new int[SAMPLES];
    Arrays.fill(xs, x);
    int[] ones = new int[SAMPLES];
    Arrays.fill(ones, 1);
    trackRepo.saveAndFlush(
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(0)
            .stepSeconds(5)
            .sampleCount(SAMPLES)
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

  private void stored(String mapVersion, List<CombatEvent> events) {
    runRepo.saveAndFlush(
        ReplayParseRunDomain.builder()
            .matchId(MATCH)
            .parserVersion(processing.getParserVersion())
            .status(ReplayStatus.DONE)
            .attempts(1)
            .mapVersion(mapVersion)
            .build());
    track(0, -1000);
    track(1, -1000);
    player(0, 0);
    player(1, 1);
    player(128, 5);
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
